package com.csquared.trekcraft.service;

import com.csquared.trekcraft.TrekCraftConfig;
import com.csquared.trekcraft.content.blockentity.TransporterRoomBlockEntity;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.RoomRecord;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalRecord;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import com.csquared.trekcraft.util.SafeTeleportFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
        SIGNAL_NOT_FOUND,
        OUT_OF_RANGE
    }

    /**
     * Result of preflight checks, containing the room to use for fuel consumption.
     */
    public record PreflightResult(TransportResult result, BlockPos roomPos) {
        public static PreflightResult success(BlockPos roomPos) {
            return new PreflightResult(TransportResult.SUCCESS, roomPos);
        }

        public static PreflightResult failure(TransportResult result) {
            return new PreflightResult(result, null);
        }

        public boolean isSuccess() {
            return result == TransportResult.SUCCESS;
        }
    }

    public static TransportResult transportToPad(ServerPlayer player, BlockPos padPos) {
        // Pre-flight checks with pad range
        PreflightResult preCheck = performPreflightChecks(player, true);
        if (!preCheck.isSuccess()) {
            return preCheck.result();
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
        return executeTransport(player, level, safeLanding.get(), data, preCheck.roomPos());
    }

    public static TransportResult transportToSignal(ServerPlayer player, UUID tricorderId) {
        // Pre-flight checks with base range
        PreflightResult preCheck = performPreflightChecks(player, false);
        if (!preCheck.isSuccess()) {
            return preCheck.result();
        }

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Get signal
        var signalOpt = data.getSignal(tricorderId);
        if (signalOpt.isEmpty()) {
            return TransportResult.SIGNAL_NOT_FOUND;
        }

        SignalRecord signal = signalOpt.get();
        BlockPos signalPos;

        // For HELD signals, get current player position if online
        if (signal.type() == SignalType.HELD && signal.holderId() != null) {
            ServerPlayer holder = level.getServer().getPlayerList().getPlayer(signal.holderId());
            if (holder != null && holder.level().dimension().equals(Level.OVERWORLD)) {
                signalPos = holder.blockPosition();
            } else {
                // Fall back to last known position
                signalPos = signal.lastKnownPos();
            }
        } else {
            signalPos = signal.lastKnownPos();
        }

        // Find safe landing
        Optional<BlockPos> safeLanding = SafeTeleportFinder.findSafeSpot(level, signalPos);
        if (safeLanding.isEmpty()) {
            return TransportResult.NO_SAFE_LANDING;
        }

        return executeTransport(player, level, safeLanding.get(), data, preCheck.roomPos());
    }

    /**
     * Perform preflight checks including range validation.
     * @param player The player attempting transport
     * @param toPad True if transporting to a pad (uses pad range), false for base range
     * @return PreflightResult containing success/failure and the room to use for fuel
     */
    private static PreflightResult performPreflightChecks(ServerPlayer player, boolean toPad) {
        // Check source is Overworld
        if (!player.level().dimension().equals(Level.OVERWORLD)) {
            return PreflightResult.failure(TransportResult.NOT_OVERWORLD_SOURCE);
        }

        // Check not riding
        if (player.isPassenger() || player.getVehicle() != null) {
            return PreflightResult.failure(TransportResult.PLAYER_RIDING);
        }

        ServerLevel level = (ServerLevel) player.level();
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        // Check any room exists
        if (!data.hasAnyRoom()) {
            return PreflightResult.failure(TransportResult.NO_TRANSPORTER_ROOM);
        }

        // Calculate effective range
        double maxRange = toPad ? TrekCraftConfig.transportPadRange : TrekCraftConfig.transportBaseRange;

        // Find nearest room within range
        Optional<RoomRecord> nearestRoom = data.getNearestRoom(player.blockPosition(), maxRange);
        if (nearestRoom.isEmpty()) {
            return PreflightResult.failure(TransportResult.OUT_OF_RANGE);
        }

        RoomRecord room = nearestRoom.get();

        // Check fuel (skip for creative)
        if (!player.isCreative() && room.cachedFuel() < 1) {
            return PreflightResult.failure(TransportResult.INSUFFICIENT_FUEL);
        }

        return PreflightResult.success(room.pos());
    }

    private static TransportResult executeTransport(ServerPlayer player, ServerLevel level,
                                                     BlockPos destination, TransporterNetworkSavedData data,
                                                     BlockPos roomPos) {
        // Consume fuel (unless creative)
        if (!player.isCreative()) {
            if (!data.consumeRoomFuel(roomPos, 1)) {
                return TransportResult.INSUFFICIENT_FUEL;
            }

            // If room block entity is loaded, also remove from inventory
            if (level.isLoaded(roomPos)) {
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
            case SIGNAL_NOT_FOUND -> "Signal lost. Tricorder may have been retrieved.";
            case OUT_OF_RANGE -> "Out of range. Move closer to a Transporter Room.";
        };
    }
}
