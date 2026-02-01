package com.csquared.trekcraft.network.mission;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.starfleet.StarfleetRank;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Payload for opening the Starfleet Command submenu in the tricorder.
 * Contains player rank info, XP, and mission counts.
 */
public record OpenStarfleetCommandPayload(
        String rankName,
        long totalXp,
        int activeMissionCount,
        int availableMissionCount,
        int completedMissionCount
) implements CustomPacketPayload {

    public static final Type<OpenStarfleetCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_starfleet_command"));

    public static final StreamCodec<FriendlyByteBuf, OpenStarfleetCommandPayload> STREAM_CODEC =
            StreamCodec.of(OpenStarfleetCommandPayload::encode, OpenStarfleetCommandPayload::decode);

    public StarfleetRank getRank() {
        try {
            return StarfleetRank.valueOf(rankName);
        } catch (Exception e) {
            return StarfleetRank.CREWMAN;
        }
    }

    public double getProgressToNextRank() {
        return StarfleetRank.getProgressToNextRank(totalXp);
    }

    public long getXpToNextRank() {
        return StarfleetRank.getXpToNextRank(totalXp);
    }

    private static void encode(FriendlyByteBuf buf, OpenStarfleetCommandPayload payload) {
        buf.writeUtf(payload.rankName);
        buf.writeLong(payload.totalXp);
        buf.writeInt(payload.activeMissionCount);
        buf.writeInt(payload.availableMissionCount);
        buf.writeInt(payload.completedMissionCount);
    }

    private static OpenStarfleetCommandPayload decode(FriendlyByteBuf buf) {
        String rankName = buf.readUtf();
        long totalXp = buf.readLong();
        int activeMissionCount = buf.readInt();
        int availableMissionCount = buf.readInt();
        int completedMissionCount = buf.readInt();
        return new OpenStarfleetCommandPayload(rankName, totalXp, activeMissionCount, availableMissionCount, completedMissionCount);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
