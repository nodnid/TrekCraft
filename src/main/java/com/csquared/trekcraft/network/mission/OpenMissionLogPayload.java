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
 * Payload for opening the player's mission log screen.
 * Contains list of active missions the player is participating in.
 */
public record OpenMissionLogPayload(
        List<ActiveMissionEntry> activeMissions
) implements CustomPacketPayload {

    public static final Type<OpenMissionLogPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_mission_log"));

    public static final StreamCodec<FriendlyByteBuf, OpenMissionLogPayload> STREAM_CODEC =
            StreamCodec.of(OpenMissionLogPayload::encode, OpenMissionLogPayload::decode);

    private static void encode(FriendlyByteBuf buf, OpenMissionLogPayload payload) {
        buf.writeInt(payload.activeMissions.size());
        for (ActiveMissionEntry entry : payload.activeMissions) {
            buf.writeUUID(entry.missionId);
            buf.writeUtf(entry.title);
            buf.writeUtf(entry.objectiveDescription);
            buf.writeInt(entry.xpReward);
            buf.writeDouble(entry.progressPercent);
            buf.writeInt(entry.totalProgress);
            buf.writeInt(entry.targetProgress);
            buf.writeInt(entry.playerContribution);
            buf.writeUtf(entry.progressText);
        }
    }

    private static OpenMissionLogPayload decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<ActiveMissionEntry> missions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            missions.add(new ActiveMissionEntry(
                    buf.readUUID(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readInt(),
                    buf.readDouble(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readInt(),
                    buf.readUtf()
            ));
        }
        return new OpenMissionLogPayload(missions);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Entry for an active mission in the player's log.
     */
    public record ActiveMissionEntry(
            UUID missionId,
            String title,
            String objectiveDescription,
            int xpReward,
            double progressPercent,
            int totalProgress,
            int targetProgress,
            int playerContribution,
            String progressText  // e.g., "12/20 kills" or "45/60s"
    ) {}
}
