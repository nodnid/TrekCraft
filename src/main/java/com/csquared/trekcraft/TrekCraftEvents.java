package com.csquared.trekcraft;

import com.csquared.trekcraft.command.TrekCommands;
import com.csquared.trekcraft.content.item.TricorderItem;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.mission.TutorialMissions;
import com.csquared.trekcraft.registry.ModBlocks;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.service.MissionService;
import com.csquared.trekcraft.service.StarfleetService;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TrekCraftMod.MODID)
public class TrekCraftEvents {

    // Track last known biome per player for exploration missions
    private static final Map<UUID, ResourceLocation> lastPlayerBiome = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TrekCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Generate tutorial missions if needed
        ServerLevel overworld = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld != null) {
            TutorialMissions.generateIfNeeded(overworld);
            StarfleetService.syncAdmiralsWithOps(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Track held tricorders as signals
        if (!TrekCraftConfig.trackHeldTricorders) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        // Only check every 20 ticks (1 second)
        if (player.tickCount % 20 != 0) return;

        ServerLevel serverLevel = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
        long gameTime = serverLevel.getGameTime();
        String dimensionKey = serverLevel.dimension().location().toString();

        // Find all tricorders in player's inventory
        Set<UUID> currentTricorders = new HashSet<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.TRICORDER.get())) {
                TricorderItem.ensureTricorderData(stack);
                TricorderData tricorderData = stack.get(ModDataComponents.TRICORDER_DATA.get());
                if (tricorderData != null) {
                    UUID tricorderId = tricorderData.tricorderId();
                    currentTricorders.add(tricorderId);

                    // Check if this signal exists
                    var existingSignal = data.getSignal(tricorderId);
                    if (existingSignal.isPresent()) {
                        // Update position if it's a held signal for this player
                        var signal = existingSignal.get();
                        if (signal.type() == TransporterNetworkSavedData.SignalType.HELD &&
                                player.getUUID().equals(signal.holderId())) {
                            data.updateSignalPosition(tricorderId, player.blockPosition(), gameTime, dimensionKey);
                        }
                        // If it was a dropped signal, convert to held
                        else if (signal.type() == TransporterNetworkSavedData.SignalType.DROPPED) {
                            data.registerHeldSignal(tricorderId, tricorderData.getDisplayName(),
                                    player.blockPosition(), gameTime, player.getUUID(), dimensionKey);
                        }
                    } else {
                        // Register new held signal
                        data.registerHeldSignal(tricorderId, tricorderData.getDisplayName(),
                                player.blockPosition(), gameTime, player.getUUID(), dimensionKey);
                    }
                }
            }
        }

        // Remove held signals for tricorders no longer in this player's inventory
        // Collect UUIDs to remove first to avoid ConcurrentModificationException
        Set<UUID> toRemove = new HashSet<>();
        for (var entry : data.getSignals().entrySet()) {
            var signal = entry.getValue();
            if (signal.type() == TransporterNetworkSavedData.SignalType.HELD &&
                    player.getUUID().equals(signal.holderId()) &&
                    !currentTricorders.contains(signal.tricorderId())) {
                toRemove.add(signal.tricorderId());
            }
        }
        for (UUID tricorderId : toRemove) {
            data.unregisterSignal(tricorderId);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // Track dropped tricorders as signals
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        if (!stack.is(ModItems.TRICORDER.get())) return;

        // Ensure it has data
        TricorderItem.ensureTricorderData(stack);
        TricorderData tricorderData = stack.get(ModDataComponents.TRICORDER_DATA.get());
        if (tricorderData == null) return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        TransporterNetworkSavedData networkData = TransporterNetworkSavedData.get(serverLevel);
        String dimensionKey = serverLevel.dimension().location().toString();

        networkData.registerDroppedSignal(
                tricorderData.tricorderId(),
                tricorderData.getDisplayName(),
                itemEntity.blockPosition(),
                serverLevel.getGameTime(),
                dimensionKey
        );

        TrekCraftMod.LOGGER.debug("Registered dropped tricorder signal: {} at {} in {}",
                tricorderData.getDisplayName(), itemEntity.blockPosition(), dimensionKey);
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        // Remove dropped tricorder signals when picked up or despawned
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) return;

        ItemStack stack = itemEntity.getItem();
        if (!stack.is(ModItems.TRICORDER.get())) return;

        TricorderData tricorderData = stack.get(ModDataComponents.TRICORDER_DATA.get());
        if (tricorderData == null) return;

        ServerLevel serverLevel = (ServerLevel) event.getLevel();
        TransporterNetworkSavedData networkData = TransporterNetworkSavedData.get(serverLevel);

        // Only unregister if it's a DROPPED signal (HELD signals are managed by player tick)
        var signal = networkData.getSignal(tricorderData.tricorderId());
        if (signal.isPresent() && signal.get().type() == TransporterNetworkSavedData.SignalType.DROPPED) {
            networkData.unregisterSignal(tricorderData.tricorderId());
            TrekCraftMod.LOGGER.debug("Unregistered dropped tricorder signal: {}", tricorderData.getDisplayName());
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockState state = event.getState();

        // Check if it's a protected TrekCraft block
        boolean isProtectedBlock = state.is(ModBlocks.HOLODECK_EMITTER.get()) ||
                                   state.is(ModBlocks.HOLODECK_CONTROLLER.get()) ||
                                   state.is(ModBlocks.MOBILE_EMITTER.get()) ||
                                   state.is(ModBlocks.TRANSPORTER_PAD.get()) ||
                                   state.is(ModBlocks.TRANSPORTER_ROOM.get());

        if (isProtectedBlock) {
            // Only allow breaking with a tricorder
            ItemStack heldItem = event.getPlayer().getMainHandItem();
            if (!heldItem.is(ModItems.TRICORDER.get())) {
                event.setCanceled(true);
            }
        }
    }

    // ===== Mission Progress Events =====

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // Track kills for mission progress
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;

        // Only track kills by players
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        // Only track hostile mobs for now (can be expanded)
        if (!(entity instanceof Monster)) return;

        MissionService.updateKillProgress(player, entity.getType());
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        // Track item pickups for gather missions
        if (event.getPlayer().level().isClientSide()) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        ItemStack stack = event.getItemEntity().getItem();
        if (stack.isEmpty()) return;

        MissionService.updateGatherProgressForItem(player, stack.getItem(), stack.getCount());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Tick defend missions and check biome exploration
        ServerLevel overworld = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        // Only run every 20 ticks (1 second)
        if (overworld.getGameTime() % 20 != 0) return;

        // Tick defend missions
        MissionService.tickDefendMissions(overworld);

        // Check biome exploration for all players
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            checkBiomeExploration(player);
        }
    }

    /**
     * Track player exploring biomes for exploration missions.
     * Only triggers when player enters a NEW biome.
     */
    private static void checkBiomeExploration(ServerPlayer player) {
        if (player.level().isClientSide()) return;

        ServerLevel level = player.serverLevel();
        Holder<Biome> biomeHolder = level.getBiome(player.blockPosition());

        // Get the biome registry name
        ResourceLocation biomeId = level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.BIOME)
                .getKey(biomeHolder.value());

        if (biomeId == null) return;

        // Check if biome changed from last known
        UUID playerId = player.getUUID();
        ResourceLocation lastBiome = lastPlayerBiome.get(playerId);

        if (!biomeId.equals(lastBiome)) {
            // Biome changed - update tracking and notify mission system
            lastPlayerBiome.put(playerId, biomeId);
            MissionService.updateExploreProgress(player, biomeId);
        }
    }

    /**
     * Clean up player biome tracking when they disconnect.
     */
    @SubscribeEvent
    public static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            lastPlayerBiome.remove(player.getUUID());
        }
    }
}
