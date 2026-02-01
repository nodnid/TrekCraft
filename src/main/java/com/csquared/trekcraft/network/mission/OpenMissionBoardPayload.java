package com.csquared.trekcraft.network.mission;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payload for opening the mission board screen.
 * Contains list of available missions.
 */
public record OpenMissionBoardPayload(
        List<MissionSummary> missions,
        String playerRankName
) implements CustomPacketPayload {

    public static final Type<OpenMissionBoardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_mission_board"));

    public static final StreamCodec<FriendlyByteBuf, OpenMissionBoardPayload> STREAM_CODEC =
            StreamCodec.of(OpenMissionBoardPayload::encode, OpenMissionBoardPayload::decode);

    private static void encode(FriendlyByteBuf buf, OpenMissionBoardPayload payload) {
        buf.writeInt(payload.missions.size());
        for (MissionSummary mission : payload.missions) {
            buf.writeUUID(mission.missionId);
            buf.writeUtf(mission.title);
            buf.writeUtf(mission.objectiveDescription);
            buf.writeInt(mission.xpReward);
            buf.writeUtf(mission.minRankName);
            buf.writeUtf(mission.statusString);
            buf.writeDouble(mission.progressPercent);
            buf.writeInt(mission.participantCount);
            buf.writeBoolean(mission.isParticipating);
            buf.writeBoolean(mission.canAccept);
        }
        buf.writeUtf(payload.playerRankName);
    }

    private static OpenMissionBoardPayload decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<MissionSummary> missions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            missions.add(new MissionSummary(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readDouble(),
                    buf.readInt(),
                    buf.readBoolean(),
                    buf.readBoolean()
            ));
        }
        String playerRankName = buf.readUtf();
        return new OpenMissionBoardPayload(missions, playerRankName);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Summary of a mission for display in the mission board.
     */
    public record MissionSummary(
            UUID missionId,
            String title,
            String objectiveDescription,
            int xpReward,
            String minRankName,
            String statusString,
            double progressPercent,
            int participantCount,
            boolean isParticipating,
            boolean canAccept
    ) {}
}
