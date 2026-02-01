package com.csquared.trekcraft.network.mission;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for opening the service record screen.
 * Contains player's Starfleet career information.
 */
public record OpenServiceRecordPayload(
        String rankName,
        long totalXp,
        long xpToNextRank,
        int activeMissionCount,
        int completedMissionCount,
        int totalKills,
        int totalScans,
        int biomesExplored
) implements CustomPacketPayload {

    public static final Type<OpenServiceRecordPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_service_record"));

    public static final StreamCodec<FriendlyByteBuf, OpenServiceRecordPayload> STREAM_CODEC =
            StreamCodec.of(OpenServiceRecordPayload::encode, OpenServiceRecordPayload::decode);

    private static void encode(FriendlyByteBuf buf, OpenServiceRecordPayload payload) {
        buf.writeUtf(payload.rankName);
        buf.writeLong(payload.totalXp);
        buf.writeLong(payload.xpToNextRank);
        buf.writeInt(payload.activeMissionCount);
        buf.writeInt(payload.completedMissionCount);
        buf.writeInt(payload.totalKills);
        buf.writeInt(payload.totalScans);
        buf.writeInt(payload.biomesExplored);
    }

    private static OpenServiceRecordPayload decode(FriendlyByteBuf buf) {
        return new OpenServiceRecordPayload(
                buf.readUtf(),
                buf.readLong(),
                buf.readLong(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Calculate progress percentage to next rank (0.0 to 1.0).
     */
    public double getProgressPercent() {
        if (xpToNextRank <= 0) return 1.0; // Already max rank
        return Math.min(1.0, (double) totalXp / (totalXp + xpToNextRank));
    }
}
