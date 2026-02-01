package com.csquared.trekcraft.network.mission;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server-to-client payload to update mission progress.
 * Sent when a mission's progress changes.
 */
public record MissionProgressUpdatePayload(
        UUID missionId,
        int totalProgress,
        int targetProgress,
        int playerContribution,
        boolean isComplete
) implements CustomPacketPayload {

    public static final Type<MissionProgressUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "mission_progress_update"));

    public static final StreamCodec<FriendlyByteBuf, MissionProgressUpdatePayload> STREAM_CODEC =
            StreamCodec.of(MissionProgressUpdatePayload::encode, MissionProgressUpdatePayload::decode);

    public double getProgressPercent() {
        if (targetProgress <= 0) return isComplete ? 1.0 : 0.0;
        return Math.min(1.0, (double) totalProgress / targetProgress);
    }

    private static void encode(FriendlyByteBuf buf, MissionProgressUpdatePayload payload) {
        buf.writeUUID(payload.missionId);
        buf.writeInt(payload.totalProgress);
        buf.writeInt(payload.targetProgress);
        buf.writeInt(payload.playerContribution);
        buf.writeBoolean(payload.isComplete);
    }

    private static MissionProgressUpdatePayload decode(FriendlyByteBuf buf) {
        return new MissionProgressUpdatePayload(
                buf.readUUID(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
