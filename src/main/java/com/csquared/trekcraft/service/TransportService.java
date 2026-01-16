package com.csquared.trekcraft.service;

import com.csquared.trekcraft.content.blockentity.TransporterRoomBlockEntity;
import com.csquared.trekcraft.content.item.TricorderItem;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.util.ChatUi;
import com.csquared.trekcraft.util.SafeTeleportFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.UUID;

public class TransportService {

    public enum TransportResult {
        SUCCESS,
        NO_TRANSPORTER_ROOM,
        NOT_OVERWORLD_SOURCE,
        NOT_OVERWORLD_DEST,
        INSUFFICIENT_FUEL,
        PLAYER_RIDING,
        DESTINATION_INVALID,
        NO_SAFE_LANDING,
        PLAYER_OFFLINE,
        TARGET_NO_TRICORDER,
        SIGNAL_NOT_FOUND
    }

    public static TransportResult transportToPad(ServerPlayer player, BlockPos padPos) {
        // Pre-flight checks
        TransportResult preCheck = performPreflightChecks(player);
        if (preCheck != TransportResult.SUCCESS) {
            return preCheck;
        }

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Check destination exists
        if (data.getPad(padPos).isEmpty()) {
            return TransportResult.DESTINATION_INVALID;
        }

        // Find safe landing spot
        Optional<BlockPos> safeLanding = SafeTeleportFinder.findSafeSpot(level, padPos.above());
        if (safeLanding.isEmpty()) {
            return TransportResult.NO_SAFE_LANDING;
        }

        // Execute transport
        return executeTransport(player, level, safeLanding.get(), data);
    }

    public static TransportResult transportToPlayer(ServerPlayer player, ServerPlayer target) {
        // Pre-flight checks
        TransportResult preCheck = performPreflightChecks(player);
        if (preCheck != TransportResult.SUCCESS) {
            return preCheck;
        }

        // Check target has a tricorder
        if (!playerHasTricorder(target)) {
            return TransportResult.TARGET_NO_TRICORDER;
        }

        // Check target is in Overworld
        if (!target.level().dimension().equals(Level.OVERWORLD)) {
            return TransportResult.NOT_OVERWORLD_DEST;
        }

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Find safe landing near target
        Optional<BlockPos> safeLanding = SafeTeleportFinder.findSafeSpot(target.level(), target.blockPosition());
        if (safeLanding.isEmpty()) {
            return TransportResult.NO_SAFE_LANDING;
        }

        // Execute transport
        return executeTransport(player, level, safeLanding.get(), data);
    }

    public static TransportResult transportToSignal(ServerPlayer player, UUID tricorderId) {
        // Pre-flight checks
        TransportResult preCheck = performPreflightChecks(player);
        if (preCheck != TransportResult.SUCCESS) {
            return preCheck;
        }

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Get signal
        var signalOpt = data.getSignal(tricorderId);
        if (signalOpt.isEmpty()) {
            return TransportResult.SIGNAL_NOT_FOUND;
        }

        BlockPos signalPos = signalOpt.get().lastKnownPos();

        // Find safe landing
        Optional<BlockPos> safeLanding = SafeTeleportFinder.findSafeSpot(level, signalPos);
        if (safeLanding.isEmpty()) {
            return TransportResult.NO_SAFE_LANDING;
        }

        return executeTransport(player, level, safeLanding.get(), data);
    }

    private static TransportResult performPreflightChecks(ServerPlayer player) {
        // Check source is Overworld
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            return TransportResult.NOT_OVERWORLD_SOURCE;
        }

        // Check not riding
        if (player.isPassenger() || player.getVehicle() != null) {
            return TransportResult.PLAYER_RIDING;
        }

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Check transporter room exists
        if (!data.hasTransporterRoom()) {
            return TransportResult.NO_TRANSPORTER_ROOM;
        }

        // Check fuel (skip for creative)
        if (!player.isCreative() && data.getCachedFuel() < 1) {
            return TransportResult.INSUFFICIENT_FUEL;
        }

        return TransportResult.SUCCESS;
    }

    private static TransportResult executeTransport(ServerPlayer player, ServerLevel level,
                                                     BlockPos destination, TransporterNetworkSavedData data) {
        // Consume fuel (unless creative)
        if (!player.isCreative()) {
            if (!data.consumeFuel(1)) {
                return TransportResult.INSUFFICIENT_FUEL;
            }

            // If room block entity is loaded, also remove from inventory
            BlockPos roomPos = data.getTransporterRoomPos();
            if (roomPos != null && level.isLoaded(roomPos)) {
                if (level.getBlockEntity(roomPos) instanceof TransporterRoomBlockEntity roomBE) {
                    roomBE.removeStrips(1);
                }
            }
        }

        // Perform teleport
        player.teleportTo(level, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                player.getYRot(), player.getXRot());

        // Effects could be added here (particles, sounds)
        return TransportResult.SUCCESS;
    }

    public static boolean playerHasTricorder(ServerPlayer player) {
        // Check main inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(ModItems.TRICORDER.get())) {
                return true;
            }
        }
        return false;
    }

    public static String getResultMessage(TransportResult result) {
        return switch (result) {
            case SUCCESS -> "Transport complete. Energize!";
            case NO_TRANSPORTER_ROOM -> "Transport offline. No active Transporter Room detected.";
            case NOT_OVERWORLD_SOURCE -> "Transport restricted to Overworld coordinates only.";
            case NOT_OVERWORLD_DEST -> "Destination coordinates outside Overworld. Transport denied.";
            case INSUFFICIENT_FUEL -> "Insufficient power reserves. Insert Latinum Strips into Transporter Room.";
            case PLAYER_RIDING -> "Pattern lock failed: dismount required.";
            case DESTINATION_INVALID -> "Destination coordinates invalid or unavailable.";
            case NO_SAFE_LANDING -> "No safe rematerialization point found. Transport aborted.";
            case PLAYER_OFFLINE -> "Target life signs not detected. Player may be offline.";
            case TARGET_NO_TRICORDER -> "Unable to establish pattern lock. Target has no tricorder signal.";
            case SIGNAL_NOT_FOUND -> "Signal lost. Tricorder may have been retrieved.";
        };
    }
}
