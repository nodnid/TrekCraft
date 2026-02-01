package com.csquared.trekcraft.service;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.data.StarfleetSavedData;
import com.csquared.trekcraft.mission.*;
import com.csquared.trekcraft.mission.objectives.*;
import com.csquared.trekcraft.starfleet.StarfleetRank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Service for mission CRUD operations and progress tracking.
 */
public class MissionService {

    /**
     * Result codes for mission operations.
     */
    public enum MissionResult {
        SUCCESS,
        INSUFFICIENT_RANK,
        MISSION_NOT_FOUND,
        ALREADY_ON_MISSION,
        NOT_ON_MISSION,
        PREREQUISITE_NOT_MET,
        PERMISSION_DENIED,
        MISSION_FULL,
        INVALID_OBJECTIVE,
        ALREADY_COMPLETED
    }

    // ===== Mission CRUD Operations =====

    /**
     * Create a new mission.
     */
    public static MissionResult createMission(
            ServerPlayer creator,
            String title,
            String description,
            MissionObjective objective,
            int xpReward,
            StarfleetRank minRank
    ) {
        StarfleetRank creatorRank = StarfleetService.getPlayerRank(creator);

        // Check if creator has permission to create missions
        if (!creatorRank.canCreateMissions()) {
            return MissionResult.INSUFFICIENT_RANK;
        }

        // Validate objective
        if (objective == null) {
            return MissionResult.INVALID_OBJECTIVE;
        }

        ServerLevel level = creator.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);

        Mission mission = Mission.create(
                title,
                description,
                objective,
                xpReward,
                minRank,
                creator.getUUID(),
                false
        );

        data.addMission(mission);

        creator.sendSystemMessage(
                Component.literal("Mission created: ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(title)
                                .withStyle(ChatFormatting.AQUA))
        );

        return MissionResult.SUCCESS;
    }

    /**
     * Create a system mission (no creator).
     */
    public static Mission createSystemMission(
            ServerLevel level,
            String title,
            String description,
            MissionObjective objective,
            int xpReward,
            StarfleetRank minRank,
            boolean isTutorial
    ) {
        StarfleetSavedData data = StarfleetSavedData.get(level);

        Mission mission = Mission.create(
                title,
                description,
                objective,
                xpReward,
                minRank,
                null,
                isTutorial
        );

        data.addMission(mission);
        return mission;
    }

    /**
     * Accept a mission.
     */
    public static MissionResult acceptMission(ServerPlayer player, UUID missionId) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        StarfleetRank playerRank = StarfleetService.getPlayerRank(player);

        Optional<Mission> missionOpt = data.getMission(missionId);
        if (missionOpt.isEmpty()) {
            return MissionResult.MISSION_NOT_FOUND;
        }

        Mission mission = missionOpt.get();

        // Check if mission can be accepted
        if (!mission.canAccept(playerRank)) {
            return MissionResult.INSUFFICIENT_RANK;
        }

        // Check if already participating
        if (mission.hasParticipant(player.getUUID())) {
            return MissionResult.ALREADY_ON_MISSION;
        }

        // Add player to mission and set to ACTIVE if first participant
        Mission updated = mission.withParticipant(player.getUUID());
        if (updated.status() == MissionStatus.POSTED) {
            updated = updated.withStatus(MissionStatus.ACTIVE);
        }

        data.updateMission(updated);

        player.sendSystemMessage(
                Component.literal("Mission accepted: ")
                        .withStyle(ChatFormatting.GREEN)
                        .append(Component.literal(mission.title())
                                .withStyle(ChatFormatting.AQUA))
        );

        return MissionResult.SUCCESS;
    }

    /**
     * Abandon a mission.
     */
    public static MissionResult abandonMission(ServerPlayer player, UUID missionId) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);

        Optional<Mission> missionOpt = data.getMission(missionId);
        if (missionOpt.isEmpty()) {
            return MissionResult.MISSION_NOT_FOUND;
        }

        Mission mission = missionOpt.get();

        if (!mission.hasParticipant(player.getUUID())) {
            return MissionResult.NOT_ON_MISSION;
        }

        Mission updated = mission.withoutParticipant(player.getUUID());

        // If no more participants and not a tutorial, set back to POSTED
        if (updated.participants().isEmpty() && !updated.isTutorial()) {
            updated = updated.withStatus(MissionStatus.POSTED);
        }

        data.updateMission(updated);

        player.sendSystemMessage(
                Component.literal("Mission abandoned: ")
                        .withStyle(ChatFormatting.YELLOW)
                        .append(Component.literal(mission.title())
                                .withStyle(ChatFormatting.GRAY))
        );

        return MissionResult.SUCCESS;
    }

    /**
     * Cancel a mission (creator or admin only).
     */
    public static MissionResult cancelMission(ServerPlayer player, UUID missionId) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        StarfleetRank playerRank = StarfleetService.getPlayerRank(player);

        Optional<Mission> missionOpt = data.getMission(missionId);
        if (missionOpt.isEmpty()) {
            return MissionResult.MISSION_NOT_FOUND;
        }

        Mission mission = missionOpt.get();

        if (!mission.canManage(player.getUUID(), playerRank)) {
            return MissionResult.PERMISSION_DENIED;
        }

        if (mission.status().isFinished()) {
            return MissionResult.ALREADY_COMPLETED;
        }

        Mission updated = mission.withStatus(MissionStatus.CANCELLED);
        data.updateMission(updated);

        player.sendSystemMessage(
                Component.literal("Mission cancelled: ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(mission.title())
                                .withStyle(ChatFormatting.GRAY))
        );

        return MissionResult.SUCCESS;
    }

    /**
     * Delete a mission (admin only).
     */
    public static MissionResult deleteMission(ServerPlayer player, UUID missionId) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        StarfleetRank playerRank = StarfleetService.getPlayerRank(player);

        if (!playerRank.canManageAllMissions()) {
            return MissionResult.PERMISSION_DENIED;
        }

        Optional<Mission> missionOpt = data.getMission(missionId);
        if (missionOpt.isEmpty()) {
            return MissionResult.MISSION_NOT_FOUND;
        }

        data.removeMission(missionId);

        player.sendSystemMessage(
                Component.literal("Mission deleted: ")
                        .withStyle(ChatFormatting.RED)
                        .append(Component.literal(missionOpt.get().title())
                                .withStyle(ChatFormatting.GRAY))
        );

        return MissionResult.SUCCESS;
    }

    // ===== Mission Queries =====

    /**
     * Get the mission board (available missions).
     */
    public static List<Mission> getMissionBoard(ServerLevel level) {
        StarfleetSavedData data = StarfleetSavedData.get(level);
        return data.getMissionBoard();
    }

    /**
     * Get a player's active missions.
     */
    public static List<Mission> getPlayerMissionLog(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        return data.getPlayerActiveMissions(player.getUUID());
    }

    /**
     * Get a mission by ID.
     */
    public static Optional<Mission> getMission(ServerLevel level, UUID missionId) {
        StarfleetSavedData data = StarfleetSavedData.get(level);
        return data.getMission(missionId);
    }

    // ===== Progress Updates =====

    /**
     * Update kill progress for a player.
     */
    public static void updateKillProgress(ServerPlayer player, EntityType<?> entityType) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        String entityTypeId = BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString();

        List<Mission> activeMissions = data.getPlayerActiveMissions(player.getUUID());
        TrekCraftMod.LOGGER.debug("Kill event: {} killed {} - checking {} active missions",
                player.getName().getString(), entityTypeId, activeMissions.size());

        for (Mission mission : activeMissions) {
            TrekCraftMod.LOGGER.debug("  Mission: {} (type: {})", mission.title(), mission.objective().getType());

            if (mission.objective() instanceof KillObjective killObj) {
                // Check if entity matches
                boolean matches = killObj.isAnyHostile() || entityTypeId.equals(killObj.entityType());
                TrekCraftMod.LOGGER.debug("    KillObjective: anyHostile={}, targetType={}, matches={}",
                        killObj.isAnyHostile(), killObj.entityType(), matches);

                if (matches) {
                    MissionProgress newProgress = mission.progress().withPlayerContribution(player.getUUID(), 1);
                    Mission updated = mission.withProgress(newProgress);

                    // Notify player of progress
                    player.displayClientMessage(
                            Component.literal("Kill progress: " + newProgress.totalProgress() + "/" + newProgress.targetProgress())
                                    .withStyle(ChatFormatting.YELLOW),
                            true
                    );

                    if (newProgress.isComplete()) {
                        completeMission(level, updated);
                    } else {
                        data.updateMission(updated);
                    }
                }
            }
        }
    }

    /**
     * Update scan progress for a player.
     */
    public static void updateScanProgress(ServerPlayer player, @Nullable ResourceLocation entityType, @Nullable ResourceLocation blockType) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);

        for (Mission mission : data.getPlayerActiveMissions(player.getUUID())) {
            if (mission.objective() instanceof ScanObjective scanObj) {
                boolean matches = false;

                if (entityType != null && scanObj.matchesEntity(entityType)) {
                    matches = true;
                } else if (blockType != null && scanObj.matchesBlock(blockType)) {
                    matches = true;
                }

                if (matches) {
                    MissionProgress newProgress = mission.progress().withPlayerContribution(player.getUUID(), 1);
                    Mission updated = mission.withProgress(newProgress);

                    // Notify player of progress
                    player.displayClientMessage(
                            Component.literal("Scan progress: " + newProgress.totalProgress() + "/" + newProgress.targetProgress())
                                    .withStyle(ChatFormatting.YELLOW),
                            true
                    );

                    if (newProgress.isComplete()) {
                        completeMission(level, updated);
                    } else {
                        data.updateMission(updated);
                    }
                }
            }
        }
    }

    /**
     * Update explore progress for a player visiting a biome.
     */
    public static void updateExploreProgress(ServerPlayer player, ResourceLocation biomeId) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        String biomeStr = biomeId.toString();

        for (Mission mission : data.getPlayerActiveMissions(player.getUUID())) {
            if (mission.objective() instanceof ExploreObjective exploreObj) {
                if (exploreObj.matchesBiome(biomeId)) {
                    MissionProgress newProgress = mission.progress().withBiomeVisited(biomeStr);

                    // Only update if biome was new
                    if (newProgress != mission.progress()) {
                        Mission updated = mission.withProgress(newProgress);

                        if (newProgress.isComplete()) {
                            completeMission(level, updated);
                        } else {
                            data.updateMission(updated);

                            // Notify of progress
                            player.displayClientMessage(
                                    Component.literal("Biome discovered! Mission progress: " +
                                                    newProgress.totalProgress() + "/" + newProgress.targetProgress())
                                            .withStyle(ChatFormatting.GREEN),
                                    true
                            );
                        }
                    }
                }
            }
        }
    }

    /**
     * Update gather progress when items are deposited.
     */
    public static void updateGatherProgress(ServerPlayer player, UUID missionId, Item item, int amount) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);

        Optional<Mission> missionOpt = data.getMission(missionId);
        if (missionOpt.isEmpty()) return;

        Mission mission = missionOpt.get();
        if (!mission.hasParticipant(player.getUUID())) return;

        if (mission.objective() instanceof GatherObjective gatherObj) {
            String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
            if (itemId.equals(gatherObj.itemId())) {
                MissionProgress newProgress = mission.progress().withPlayerContribution(player.getUUID(), amount);
                Mission updated = mission.withProgress(newProgress);

                if (newProgress.isComplete()) {
                    completeMission(level, updated);
                } else {
                    data.updateMission(updated);
                }
            }
        }
    }

    /**
     * Update gather progress for all matching gather missions when items are picked up.
     */
    public static void updateGatherProgressForItem(ServerPlayer player, Item item, int amount) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

        for (Mission mission : data.getPlayerActiveMissions(player.getUUID())) {
            if (mission.objective() instanceof GatherObjective gatherObj) {
                if (itemId.equals(gatherObj.itemId())) {
                    MissionProgress newProgress = mission.progress().withPlayerContribution(player.getUUID(), amount);
                    Mission updated = mission.withProgress(newProgress);

                    // Notify player of progress
                    player.displayClientMessage(
                            Component.literal("Gather progress: " + newProgress.totalProgress() + "/" + newProgress.targetProgress())
                                    .withStyle(ChatFormatting.YELLOW),
                            true
                    );

                    if (newProgress.isComplete()) {
                        completeMission(level, updated);
                    } else {
                        data.updateMission(updated);
                    }
                }
            }
        }
    }

    /**
     * Update contribution progress when items are contributed.
     */
    public static void updateContributionProgress(ServerPlayer player, UUID missionId, int amount) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);

        Optional<Mission> missionOpt = data.getMission(missionId);
        if (missionOpt.isEmpty()) return;

        Mission mission = missionOpt.get();
        if (!mission.hasParticipant(player.getUUID())) return;

        if (mission.objective() instanceof ContributionObjective contribObj) {
            MissionProgress newProgress = mission.progress().withPlayerContribution(player.getUUID(), amount);
            Mission updated = mission.withProgress(newProgress);

            // Award XP immediately for contribution objectives
            int xpEarned = contribObj.calculateXp(amount);
            if (xpEarned > 0) {
                StarfleetService.awardXp(player, xpEarned);
            }

            if (newProgress.isComplete()) {
                completeMission(level, updated);
            } else {
                data.updateMission(updated);
            }
        }
    }

    /**
     * Update contribution progress for all matching contribution missions when items are contributed.
     * This finds all active contribution missions the player is on that accept the given item.
     */
    public static void updateContributionProgressForItem(ServerPlayer player, Item item, int amount) {
        ServerLevel level = player.serverLevel();
        StarfleetSavedData data = StarfleetSavedData.get(level);
        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();

        TrekCraftMod.LOGGER.debug("Checking contribution missions for {} contributing {} x{}",
                player.getName().getString(), itemId, amount);

        for (Mission mission : data.getPlayerActiveMissions(player.getUUID())) {
            if (mission.objective() instanceof ContributionObjective contribObj) {
                if (itemId.equals(contribObj.itemId())) {
                    TrekCraftMod.LOGGER.debug("Found matching contribution mission: {}", mission.title());

                    MissionProgress newProgress = mission.progress().withPlayerContribution(player.getUUID(), amount);
                    Mission updated = mission.withProgress(newProgress);

                    // Award XP immediately for contribution objectives
                    int xpEarned = contribObj.calculateXp(amount);
                    if (xpEarned > 0) {
                        StarfleetService.awardXp(player, xpEarned);
                        player.displayClientMessage(
                                Component.literal("+" + xpEarned + " XP from " + mission.title())
                                        .withStyle(ChatFormatting.GREEN),
                                true
                        );
                    }

                    if (newProgress.isComplete()) {
                        completeMission(level, updated);
                    } else {
                        data.updateMission(updated);
                    }
                }
            }
        }
    }

    /**
     * Update defend mission timer tick.
     */
    public static void tickDefendMissions(ServerLevel level) {
        StarfleetSavedData data = StarfleetSavedData.get(level);

        for (Mission mission : data.getMissionsByStatus(MissionStatus.ACTIVE)) {
            if (mission.objective() instanceof DefendObjective defendObj) {
                // Check if any participant is in the defend zone
                boolean someonePresent = false;
                for (UUID participantId : mission.participants()) {
                    ServerPlayer participant = level.getServer().getPlayerList().getPlayer(participantId);
                    if (participant != null) {
                        String playerDimension = participant.level().dimension().location().toString();
                        if (defendObj.isWithinArea(participant.blockPosition(), playerDimension)) {
                            someonePresent = true;
                            break;
                        }
                    }
                }

                if (someonePresent) {
                    MissionProgress newProgress = mission.progress().withDefendTick(1);
                    Mission updated = mission.withProgress(newProgress);

                    // Notify participants of progress (every 20 ticks = 1 second)
                    if (level.getGameTime() % 20 == 0) {
                        long totalTicks = defendObj.durationTicks();
                        long remaining = newProgress.defendTicksRemaining();
                        int secondsRemaining = (int)(remaining / 20);
                        int totalSeconds = (int)(totalTicks / 20);
                        int secondsElapsed = totalSeconds - secondsRemaining;

                        for (UUID participantId : mission.participants()) {
                            ServerPlayer participant = level.getServer().getPlayerList().getPlayer(participantId);
                            if (participant != null) {
                                participant.displayClientMessage(
                                    Component.literal("Defend progress: " + secondsElapsed + "/" + totalSeconds + "s")
                                        .withStyle(ChatFormatting.YELLOW),
                                    true
                                );
                            }
                        }
                    }

                    if (newProgress.isComplete()) {
                        completeMission(level, updated);
                    } else {
                        data.updateMission(updated);
                    }
                }
            }
        }
    }

    // ===== Mission Completion =====

    /**
     * Complete a mission and distribute rewards.
     */
    private static void completeMission(ServerLevel level, Mission mission) {
        StarfleetSavedData data = StarfleetSavedData.get(level);

        Mission completed = mission.withStatus(MissionStatus.COMPLETED);
        data.updateMission(completed);

        // Distribute rewards to participants
        distributeRewards(level, completed);

        TrekCraftMod.LOGGER.info("Mission completed: {}", mission.title());
    }

    /**
     * Distribute rewards to mission participants.
     */
    private static void distributeRewards(ServerLevel level, Mission mission) {
        StarfleetSavedData data = StarfleetSavedData.get(level);
        MissionProgress progress = mission.progress();

        boolean isProportional = isProportionalReward(mission.objective());

        for (UUID participantId : mission.participants()) {
            int xpReward;

            if (isProportional && progress.totalProgress() > 0) {
                // Proportional reward based on contribution
                int contribution = progress.getPlayerContribution(participantId);
                double fraction = (double) contribution / progress.totalProgress();
                xpReward = (int) (mission.xpReward() * fraction);
            } else {
                // Equal reward for team effort objectives
                int participantCount = mission.participants().size();
                xpReward = mission.xpReward() / Math.max(1, participantCount);
            }

            if (xpReward > 0) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(participantId);
                if (player != null) {
                    // Award XP
                    StarfleetService.awardXp(player, xpReward);

                    // Add to mission history
                    data.addMissionToHistory(participantId, player.getName().getString(), mission.missionId());

                    // Notify of completion
                    player.sendSystemMessage(
                            Component.literal("★ MISSION COMPLETE ★ ")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                    .append(Component.literal(mission.title())
                                            .withStyle(ChatFormatting.AQUA))
                    );
                } else {
                    // Player offline - just record the history, they'll get XP on next login if we implement that
                    data.addMissionToHistory(participantId, "Unknown", mission.missionId());
                }
            }
        }
    }

    /**
     * Check if an objective uses proportional rewards.
     */
    private static boolean isProportionalReward(MissionObjective objective) {
        return switch (objective) {
            case GatherObjective ignored -> true;
            case KillObjective ignored -> true;
            case BuildObjective ignored -> true;
            case ContributionObjective ignored -> true; // Though contribution handles XP per-item
            case ScanObjective ignored -> false; // Team effort
            case ExploreObjective ignored -> false; // Team effort
            case DefendObjective ignored -> false; // Team effort
            case CompositeObjective ignored -> false; // Complex - default to equal
            default -> false; // Unknown objective type - default to equal
        };
    }

    /**
     * Get result message for display.
     */
    public static String getResultMessage(MissionResult result) {
        return switch (result) {
            case SUCCESS -> "Operation successful.";
            case INSUFFICIENT_RANK -> "Insufficient rank for this operation.";
            case MISSION_NOT_FOUND -> "Mission not found.";
            case ALREADY_ON_MISSION -> "You are already on this mission.";
            case NOT_ON_MISSION -> "You are not participating in this mission.";
            case PREREQUISITE_NOT_MET -> "Mission prerequisites not met.";
            case PERMISSION_DENIED -> "You do not have permission for this operation.";
            case MISSION_FULL -> "Mission is at capacity.";
            case INVALID_OBJECTIVE -> "Invalid mission objective.";
            case ALREADY_COMPLETED -> "Mission is already completed or cancelled.";
        };
    }
}
