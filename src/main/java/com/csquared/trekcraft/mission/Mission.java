package com.csquared.trekcraft.mission;

import com.csquared.trekcraft.mission.objectives.ContributionObjective;
import com.csquared.trekcraft.mission.objectives.DefendObjective;
import com.csquared.trekcraft.starfleet.StarfleetRank;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Represents a Starfleet mission that players can accept and complete.
 *
 * @param missionId       Unique identifier for this mission
 * @param title           Display title of the mission
 * @param description     Full description of the mission
 * @param objective       The objective that must be completed
 * @param status          Current status of the mission
 * @param progress        Progress tracking data
 * @param xpReward        Base XP reward for completion
 * @param minRank         Minimum rank required to accept this mission
 * @param creatorId       UUID of the player who created the mission (null for system missions)
 * @param createdTime     System time when mission was created
 * @param completedTime   System time when mission was completed (0 if not completed)
 * @param participants    Set of player UUIDs who have accepted this mission
 * @param isTutorial      Whether this is a tutorial mission
 */
public record Mission(
        UUID missionId,
        String title,
        String description,
        MissionObjective objective,
        MissionStatus status,
        MissionProgress progress,
        int xpReward,
        StarfleetRank minRank,
        @Nullable UUID creatorId,
        long createdTime,
        long completedTime,
        Set<UUID> participants,
        boolean isTutorial
) {
    /**
     * Create a new mission with POSTED status.
     */
    public static Mission create(
            String title,
            String description,
            MissionObjective objective,
            int xpReward,
            StarfleetRank minRank,
            @Nullable UUID creatorId,
            boolean isTutorial
    ) {
        int targetProgress = getTargetProgressForObjective(objective);
        MissionProgress initialProgress;

        if (objective instanceof DefendObjective defend) {
            initialProgress = MissionProgress.forDefend(defend.durationTicks());
        } else if (objective.getType() == MissionObjective.ObjectiveType.EXPLORE) {
            initialProgress = MissionProgress.forExplore(targetProgress);
        } else {
            initialProgress = MissionProgress.initial(targetProgress);
        }

        return new Mission(
                UUID.randomUUID(),
                title,
                description,
                objective,
                MissionStatus.POSTED,
                initialProgress,
                xpReward,
                minRank,
                creatorId,
                System.currentTimeMillis(),
                0,
                new HashSet<>(),
                isTutorial
        );
    }

    /**
     * Determine target progress from objective type.
     */
    private static int getTargetProgressForObjective(MissionObjective objective) {
        return switch (objective) {
            case com.csquared.trekcraft.mission.objectives.GatherObjective g -> g.quantity();
            case com.csquared.trekcraft.mission.objectives.KillObjective k -> k.count();
            case com.csquared.trekcraft.mission.objectives.ScanObjective s -> s.count();
            case com.csquared.trekcraft.mission.objectives.ExploreObjective e -> e.count();
            case DefendObjective d -> 1; // Defend is time-based, not count-based
            case ContributionObjective c -> c.targetAmount();
            case com.csquared.trekcraft.mission.objectives.BuildObjective b -> b.totalBlocks();
            case com.csquared.trekcraft.mission.objectives.CompositeObjective comp -> comp.objectives().size();
            default -> 1; // Unknown objective type
        };
    }

    /**
     * Create a copy with updated status.
     */
    public Mission withStatus(MissionStatus newStatus) {
        long completed = newStatus == MissionStatus.COMPLETED ? System.currentTimeMillis() : completedTime;
        return new Mission(missionId, title, description, objective, newStatus, progress, xpReward, minRank, creatorId, createdTime, completed, participants, isTutorial);
    }

    /**
     * Create a copy with updated progress.
     */
    public Mission withProgress(MissionProgress newProgress) {
        return new Mission(missionId, title, description, objective, status, newProgress, xpReward, minRank, creatorId, createdTime, completedTime, participants, isTutorial);
    }

    /**
     * Create a copy with a player added to participants.
     */
    public Mission withParticipant(UUID playerId) {
        Set<UUID> newParticipants = new HashSet<>(participants);
        newParticipants.add(playerId);
        return new Mission(missionId, title, description, objective, status, progress, xpReward, minRank, creatorId, createdTime, completedTime, newParticipants, isTutorial);
    }

    /**
     * Create a copy with a player removed from participants.
     */
    public Mission withoutParticipant(UUID playerId) {
        Set<UUID> newParticipants = new HashSet<>(participants);
        newParticipants.remove(playerId);
        return new Mission(missionId, title, description, objective, status, progress, xpReward, minRank, creatorId, createdTime, completedTime, newParticipants, isTutorial);
    }

    /**
     * Check if a player is participating in this mission.
     */
    public boolean hasParticipant(UUID playerId) {
        return participants.contains(playerId);
    }

    /**
     * Check if the mission is complete.
     */
    public boolean isComplete() {
        return progress.isComplete();
    }

    /**
     * Check if a player can accept this mission.
     */
    public boolean canAccept(StarfleetRank playerRank) {
        if (!status.canAccept()) return false;
        return playerRank.canAcceptMission(minRank);
    }

    /**
     * Check if a player can manage (edit/cancel) this mission.
     */
    public boolean canManage(UUID playerId, StarfleetRank playerRank) {
        // Admirals can manage any mission
        if (playerRank.canManageAllMissions()) return true;
        // Creators can manage their own missions
        return creatorId != null && creatorId.equals(playerId);
    }

    /**
     * Get a short status string for display.
     */
    public String getStatusString() {
        return switch (status) {
            case POSTED -> "Available";
            case ACTIVE -> String.format("In Progress (%.0f%%)", progress.getProgressPercent() * 100);
            case COMPLETED -> "Completed";
            case CANCELLED -> "Cancelled";
        };
    }

    /**
     * Save to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("MissionId", missionId);
        tag.putString("Title", title);
        tag.putString("Description", description);
        tag.put("Objective", objective.save());
        tag.putString("Status", status.name());
        tag.put("Progress", progress.save());
        tag.putInt("XpReward", xpReward);
        tag.putString("MinRank", minRank.name());
        if (creatorId != null) {
            tag.putUUID("CreatorId", creatorId);
        }
        tag.putLong("CreatedTime", createdTime);
        tag.putLong("CompletedTime", completedTime);
        tag.putBoolean("IsTutorial", isTutorial);

        // Save participants
        ListTag participantList = new ListTag();
        for (UUID participant : participants) {
            CompoundTag pTag = new CompoundTag();
            pTag.putUUID("PlayerId", participant);
            participantList.add(pTag);
        }
        tag.put("Participants", participantList);

        return tag;
    }

    /**
     * Load from NBT.
     */
    public static Mission load(CompoundTag tag) {
        UUID missionId = tag.getUUID("MissionId");
        String title = tag.getString("Title");
        String description = tag.getString("Description");
        MissionObjective objective = MissionObjective.load(tag.getCompound("Objective"));
        MissionStatus status = MissionStatus.valueOf(tag.getString("Status"));
        MissionProgress progress = MissionProgress.load(tag.getCompound("Progress"));
        int xpReward = tag.getInt("XpReward");
        StarfleetRank minRank = StarfleetRank.valueOf(tag.getString("MinRank"));
        UUID creatorId = tag.contains("CreatorId") ? tag.getUUID("CreatorId") : null;
        long createdTime = tag.getLong("CreatedTime");
        long completedTime = tag.getLong("CompletedTime");
        boolean isTutorial = tag.getBoolean("IsTutorial");

        Set<UUID> participants = new HashSet<>();
        if (tag.contains("Participants", Tag.TAG_LIST)) {
            ListTag participantList = tag.getList("Participants", Tag.TAG_COMPOUND);
            for (int i = 0; i < participantList.size(); i++) {
                participants.add(participantList.getCompound(i).getUUID("PlayerId"));
            }
        }

        return new Mission(missionId, title, description, objective, status, progress, xpReward, minRank, creatorId, createdTime, completedTime, participants, isTutorial);
    }
}
