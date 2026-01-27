package com.csquared.trekcraft;

import com.csquared.trekcraft.command.TrekCommands;
import com.csquared.trekcraft.content.item.TricorderItem;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TricorderData;
import com.csquared.trekcraft.registry.ModDataComponents;
import com.csquared.trekcraft.registry.ModItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = TrekCraftMod.MODID)
public class TrekCraftEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        TrekCommands.register(event.getDispatcher());
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
}
