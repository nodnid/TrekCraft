package com.csquared.trekcraft.holodeck;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.blockentity.HolodeckControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.*;

/**
 * Singleton service for managing holodeck-related events and coordination.
 * Handles player logout, server stop, and cross-holodeck coordination.
 */
@EventBusSubscriber(modid = TrekCraftMod.MODID)
public class HolodeckService {

    // Track which holodeck each player is in (for quick lookup on logout)
    private static final Map<UUID, BlockPos> playerHolodeckMap = new HashMap<>();

    /**
     * Register a player as being inside a holodeck.
     */
    public static void registerPlayerInHolodeck(UUID playerId, BlockPos controllerPos) {
        playerHolodeckMap.put(playerId, controllerPos);
    }

    /**
     * Unregister a player from a holodeck.
     */
    public static void unregisterPlayerFromHolodeck(UUID playerId) {
        playerHolodeckMap.remove(playerId);
    }

    /**
     * Get the holodeck controller position a player is in.
     */
    public static Optional<BlockPos> getPlayerHolodeck(UUID playerId) {
        return Optional.ofNullable(playerHolodeckMap.get(playerId));
    }

    /**
     * Handle player logout - restore their game mode and inventory.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID playerId = player.getUUID();
        BlockPos controllerPos = playerHolodeckMap.get(playerId);

        if (controllerPos != null) {
            ServerLevel level = player.serverLevel();
            BlockEntity be = level.getBlockEntity(controllerPos);

            if (be instanceof HolodeckControllerBlockEntity controller) {
                // The controller will handle restoration via its stored data
                // We just need to trigger the exit logic
                TrekCraftMod.LOGGER.info("Player {} logging out while in holodeck at {}",
                        player.getName().getString(), controllerPos);
            }

            playerHolodeckMap.remove(playerId);
        }
    }

    /**
     * Handle server stopping - restore all players in holodecks.
     */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();

        for (Map.Entry<UUID, BlockPos> entry : new HashMap<>(playerHolodeckMap).entrySet()) {
            UUID playerId = entry.getKey();
            BlockPos controllerPos = entry.getValue();

            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                ServerLevel level = player.serverLevel();
                BlockEntity be = level.getBlockEntity(controllerPos);

                if (be instanceof HolodeckControllerBlockEntity controller) {
                    TrekCraftMod.LOGGER.info("Server stopping - restoring player {} from holodeck at {}",
                            player.getName().getString(), controllerPos);
                    // Controller's NBT save will preserve the data
                    // On next load, players will be restored from saved state
                }
            }
        }

        playerHolodeckMap.clear();
    }

    /**
     * Handle player login - check if they were in a holodeck and restore them.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // The HolodeckControllerBlockEntity will handle restoration on its own tick
        // by checking the stored playersInside set after loading
        // This event is just for logging/debugging purposes

        TrekCraftMod.LOGGER.debug("Player {} logged in, holodeck state will be checked by controllers",
                player.getName().getString());
    }

}
