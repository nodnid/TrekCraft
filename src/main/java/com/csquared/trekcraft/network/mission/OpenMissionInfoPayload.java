package com.csquared.trekcraft.network.mission;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Payload for opening the mission info screen with detailed mission data.
 */
public record OpenMissionInfoPayload(
        UUID missionId,
        String title,
        String description,
        String objectiveDescription,
        int xpReward,
        String minRankTitle,
        String statusString,
        double progressPercent,
        int totalProgress,
        int targetProgress,
        String progressText,
        int participantCount,
        boolean isParticipating,
        boolean canAccept
) implements CustomPacketPayload {

    public static final Type<OpenMissionInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_mission_info"));

    public static final StreamCodec<FriendlyByteBuf, OpenMissionInfoPayload> STREAM_CODEC =
            StreamCodec.of(OpenMissionInfoPayload::encode, OpenMissionInfoPayload::decode);

    private static void encode(FriendlyByteBuf buf, OpenMissionInfoPayload payload) {
        buf.writeUUID(payload.missionId);
        buf.writeUtf(payload.title);
        buf.writeUtf(payload.description);
        buf.writeUtf(payload.objectiveDescription);
        buf.writeInt(payload.xpReward);
        buf.writeUtf(payload.minRankTitle);
        buf.writeUtf(payload.statusString);
        buf.writeDouble(payload.progressPercent);
        buf.writeInt(payload.totalProgress);
        buf.writeInt(payload.targetProgress);
        buf.writeUtf(payload.progressText);
        buf.writeInt(payload.participantCount);
        buf.writeBoolean(payload.isParticipating);
        buf.writeBoolean(payload.canAccept);
    }

    private static OpenMissionInfoPayload decode(FriendlyByteBuf buf) {
        return new OpenMissionInfoPayload(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readDouble(),
                buf.readInt(),
                buf.readInt(),
                buf.readUtf(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
