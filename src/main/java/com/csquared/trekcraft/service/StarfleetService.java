package com.csquared.trekcraft.service;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.data.StarfleetSavedData;
import com.csquared.trekcraft.starfleet.StarfleetRank;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Service for Starfleet rank and XP management.
 * Follows TransportService pattern with static methods.
 */
public class StarfleetService {

    /**
     * Award XP to a player and handle rank-up notifications.
     *
     * @param player The player to award XP to
     * @param xp     The amount of XP to award
     * @return The new rank if the player was promoted, null otherwise
     */
    public static StarfleetRank awardXp(ServerPlayer player, int xp) {
        if (xp <= 0) return null;

        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);

        StarfleetRank newRank = data.awardXp(player.getUUID(), player.getName().getString(), xp);

        // Notify player of XP gain
        player.displayClientMessage(
                Component.literal("+" + xp + " Starfleet XP")
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        // Handle rank-up
        if (newRank != null) {
            sendRankUpNotification(player, newRank);
        }

        return newRank;
    }

    /**
     * Send rank-up notification to player.
     */
    private static void sendRankUpNotification(ServerPlayer player, StarfleetRank newRank) {
        player.sendSystemMessage(
                Component.literal("★ PROMOTION ★ ")
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        .append(Component.literal("You have achieved the rank of ")
                                .withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(newRank.getTitle())
                                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                        .append(Component.literal("!")
                                .withStyle(ChatFormatting.YELLOW))
        );

        // Notify about new abilities
        if (newRank.canCreateMissions()) {
            player.sendSystemMessage(
                    Component.literal("You can now create missions for other crew members!")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
    }

    /**
     * Get a player's current rank.
     */
    public static StarfleetRank getPlayerRank(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);

        // Check if player is a server op - they get Admiral rank
        if (player.hasPermissions(2)) {
            return StarfleetRank.ADMIRAL;
        }

        // Check manual admiral status
        if (data.isAdmiral(player.getUUID())) {
            return StarfleetRank.ADMIRAL;
        }

        return data.getPlayerRank(player.getUUID());
    }

    /**
     * Get a player's current rank by UUID (for offline players).
     */
    public static StarfleetRank getPlayerRank(ServerLevel level, UUID playerId) {
        StarfleetSavedData data = StarfleetSavedData.get(level);

        // Check manual admiral status
        if (data.isAdmiral(playerId)) {
            return StarfleetRank.ADMIRAL;
        }

        return data.getPlayerRank(playerId);
    }

    /**
     * Get a player's total XP.
     */
    public static long getPlayerXp(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        return data.getPlayerXp(player.getUUID());
    }

    /**
     * Check if a player is an admiral (either through op or manual assignment).
     */
    public static boolean isAdmiral(ServerPlayer player) {
        return getPlayerRank(player) == StarfleetRank.ADMIRAL;
    }

    /**
     * Check if a UUID is an admiral (for offline checks).
     */
    public static boolean isAdmiral(ServerLevel level, UUID playerId) {
        StarfleetSavedData data = StarfleetSavedData.get(level);

        // Check manual assignment
        if (data.isAdmiral(playerId)) {
            return true;
        }

        // Check if they're a server op (only works if they're online)
        MinecraftServer server = level.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        return player != null && player.hasPermissions(2);
    }

    /**
     * Grant admiral status to a player.
     */
    public static void grantAdmiral(ServerLevel level, UUID playerId) {
        StarfleetSavedData data = StarfleetSavedData.get(level);
        data.grantAdmiral(playerId);

        // Notify if online
        MinecraftServer server = level.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(
                    Component.literal("★ ADMIRAL ★ ")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                            .append(Component.literal("You have been granted Admiral status!")
                                    .withStyle(ChatFormatting.YELLOW))
            );
        }
    }

    /**
     * Revoke admiral status from a player.
     */
    public static void revokeAdmiral(ServerLevel level, UUID playerId) {
        StarfleetSavedData data = StarfleetSavedData.get(level);
        data.revokeAdmiral(playerId);

        // Notify if online
        MinecraftServer server = level.getServer();
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            player.sendSystemMessage(
                    Component.literal("Your Admiral status has been revoked.")
                            .withStyle(ChatFormatting.YELLOW)
            );
        }
    }

    /**
     * Get all manually assigned admirals.
     */
    public static Set<UUID> getManualAdmirals(ServerLevel level) {
        StarfleetSavedData data = StarfleetSavedData.get(level);
        return data.getAdmirals();
    }

    /**
     * Sync admiral status with server ops.
     * Called on server start to ensure ops have admiral status.
     * Note: Ops automatically get Admiral rank in getPlayerRank() when online.
     */
    public static void syncAdmiralsWithOps(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        // Ops automatically get admiral in getPlayerRank() when online,
        // so no explicit sync is needed. This method is for future use if
        // we want to persist op admiral status for offline checks.
        TrekCraftMod.LOGGER.info("Starfleet admiral sync complete.");
    }

    /**
     * Set a player's XP directly (admin command).
     */
    public static void setXp(ServerLevel level, UUID playerId, String playerName, long xp) {
        StarfleetSavedData data = StarfleetSavedData.get(level);
        data.setXp(playerId, playerName, xp);
    }

    /**
     * Get formatted rank info for display.
     */
    public static Component getRankDisplay(ServerPlayer player) {
        StarfleetRank rank = getPlayerRank(player);
        long xp = getPlayerXp(player);
        StarfleetRank nextRank = rank.getNextRank();

        Component rankComponent = Component.literal(rank.getTitle())
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);

        if (rank == StarfleetRank.ADMIRAL) {
            return Component.literal("Rank: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(rankComponent)
                    .append(Component.literal(" (Command Level)")
                            .withStyle(ChatFormatting.GOLD));
        }

        if (nextRank != null) {
            long xpToNext = StarfleetRank.getXpToNextRank(xp);
            double progress = StarfleetRank.getProgressToNextRank(xp);

            return Component.literal("Rank: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(rankComponent)
                    .append(Component.literal(String.format(" (%d XP | %.0f%% to %s)",
                                    xp, progress * 100, nextRank.getTitle()))
                            .withStyle(ChatFormatting.GRAY));
        } else {
            return Component.literal("Rank: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(rankComponent)
                    .append(Component.literal(String.format(" (%d XP | Max Rank)", xp))
                            .withStyle(ChatFormatting.GRAY));
        }
    }
}
