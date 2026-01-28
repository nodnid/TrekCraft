package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.data.ContributorRank;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload for sending contribution screen data from server to client.
 */
public record OpenContributionScreenPayload(
        long totalDeposited,
        long totalWithdrawn,
        int freeTransportsUsed,
        String highestRankName,
        List<LeaderboardEntry> leaderboard
) implements CustomPacketPayload {

    public static final Type<OpenContributionScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_contribution_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenContributionScreenPayload> STREAM_CODEC =
            StreamCodec.of(OpenContributionScreenPayload::encode, OpenContributionScreenPayload::decode);

    public long getNetContribution() {
        return totalDeposited - totalWithdrawn;
    }

    public int getFreeTransportsRemaining() {
        return Math.max(0, (int)(getNetContribution() / 10) - freeTransportsUsed);
    }

    public int getTotalFreeTransportsEarned() {
        return (int)(getNetContribution() / 10);
    }

    public ContributorRank getHighestRank() {
        try {
            return ContributorRank.valueOf(highestRankName);
        } catch (Exception e) {
            return ContributorRank.CREWMAN;
        }
    }

    private static void encode(FriendlyByteBuf buf, OpenContributionScreenPayload payload) {
        buf.writeLong(payload.totalDeposited);
        buf.writeLong(payload.totalWithdrawn);
        buf.writeInt(payload.freeTransportsUsed);
        buf.writeUtf(payload.highestRankName);

        buf.writeInt(payload.leaderboard.size());
        for (LeaderboardEntry entry : payload.leaderboard) {
            buf.writeUtf(entry.playerName);
            buf.writeLong(entry.netContribution);
            buf.writeUtf(entry.rankName);
        }
    }

    private static OpenContributionScreenPayload decode(FriendlyByteBuf buf) {
        long totalDeposited = buf.readLong();
        long totalWithdrawn = buf.readLong();
        int freeTransportsUsed = buf.readInt();
        String highestRankName = buf.readUtf();

        int leaderboardSize = buf.readInt();
        List<LeaderboardEntry> leaderboard = new ArrayList<>();
        for (int i = 0; i < leaderboardSize; i++) {
            String playerName = buf.readUtf();
            long netContribution = buf.readLong();
            String rankName = buf.readUtf();
            leaderboard.add(new LeaderboardEntry(playerName, netContribution, rankName));
        }

        return new OpenContributionScreenPayload(totalDeposited, totalWithdrawn, freeTransportsUsed, highestRankName, leaderboard);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record LeaderboardEntry(String playerName, long netContribution, String rankName) {
        public ContributorRank getRank() {
            try {
                return ContributorRank.valueOf(rankName);
            } catch (Exception e) {
                return ContributorRank.CREWMAN;
            }
        }
    }
}
