package com.csquared.trekcraft.mission;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks progress of a mission including per-player contributions.
 *
 * @param totalProgress    Current progress towards objective completion
 * @param targetProgress   Target progress for completion
 * @param playerContributions Map of player UUID to their contribution amount
 * @param visitedBiomes    Set of biome IDs visited (for explore missions)
 * @param defendTicksRemaining Ticks remaining for defend missions
 */
public record MissionProgress(
        int totalProgress,
        int targetProgress,
        Map<UUID, Integer> playerContributions,
        Set<String> visitedBiomes,
        long defendTicksRemaining
) {
    /**
     * Create initial progress for a mission.
     */
    public static MissionProgress initial(int targetProgress) {
        return new MissionProgress(0, targetProgress, new HashMap<>(), new java.util.HashSet<>(), 0);
    }

    /**
     * Create initial progress for a defend mission.
     */
    public static MissionProgress forDefend(long totalTicks) {
        return new MissionProgress(0, 1, new HashMap<>(), new java.util.HashSet<>(), totalTicks);
    }

    /**
     * Create initial progress for an explore mission.
     */
    public static MissionProgress forExplore(int targetBiomes) {
        return new MissionProgress(0, targetBiomes, new HashMap<>(), new java.util.HashSet<>(), 0);
    }

    /**
     * Check if mission is complete.
     */
    public boolean isComplete() {
        // Standard progress-based completion
        if (targetProgress > 0 && totalProgress >= targetProgress) {
            return true;
        }
        // Defend missions: targetProgress is 1, and completion happens when timer expires
        // Only check defend logic if this was a defend mission (indicated by having had defend ticks)
        // Note: defend missions use targetProgress=1, so they complete via the first check
        // when totalProgress is set to 1 after timer expires
        return false;
    }

    /**
     * Get progress as a percentage (0.0 to 1.0).
     */
    public double getProgressPercent() {
        if (targetProgress <= 0) {
            return defendTicksRemaining <= 0 ? 1.0 : 0.0;
        }
        return Math.min(1.0, (double) totalProgress / targetProgress);
    }

    /**
     * Get a player's contribution amount.
     */
    public int getPlayerContribution(UUID playerId) {
        return playerContributions.getOrDefault(playerId, 0);
    }

    /**
     * Get total number of unique contributors.
     */
    public int getContributorCount() {
        return playerContributions.size();
    }

    /**
     * Create new progress with updated total.
     */
    public MissionProgress withProgress(int newProgress) {
        return new MissionProgress(newProgress, targetProgress, playerContributions, visitedBiomes, defendTicksRemaining);
    }

    /**
     * Create new progress adding a player contribution.
     */
    public MissionProgress withPlayerContribution(UUID playerId, int amount) {
        Map<UUID, Integer> newContribs = new HashMap<>(playerContributions);
        newContribs.merge(playerId, amount, Integer::sum);
        return new MissionProgress(totalProgress + amount, targetProgress, newContribs, visitedBiomes, defendTicksRemaining);
    }

    /**
     * Create new progress with a biome visited.
     */
    public MissionProgress withBiomeVisited(String biomeId) {
        if (visitedBiomes.contains(biomeId)) {
            return this;
        }
        Set<String> newBiomes = new java.util.HashSet<>(visitedBiomes);
        newBiomes.add(biomeId);
        return new MissionProgress(newBiomes.size(), targetProgress, playerContributions, newBiomes, defendTicksRemaining);
    }

    /**
     * Create new progress with defend ticks decremented.
     */
    public MissionProgress withDefendTick(long ticksToSubtract) {
        long newTicks = Math.max(0, defendTicksRemaining - ticksToSubtract);
        int newProgress = newTicks <= 0 ? 1 : 0;
        return new MissionProgress(newProgress, targetProgress, playerContributions, visitedBiomes, newTicks);
    }

    /**
     * Save to NBT.
     */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("TotalProgress", totalProgress);
        tag.putInt("TargetProgress", targetProgress);
        tag.putLong("DefendTicksRemaining", defendTicksRemaining);

        // Save player contributions
        ListTag contribList = new ListTag();
        for (var entry : playerContributions.entrySet()) {
            CompoundTag contribTag = new CompoundTag();
            contribTag.putUUID("PlayerId", entry.getKey());
            contribTag.putInt("Amount", entry.getValue());
            contribList.add(contribTag);
        }
        tag.put("PlayerContributions", contribList);

        // Save visited biomes
        ListTag biomeList = new ListTag();
        for (String biome : visitedBiomes) {
            CompoundTag biomeTag = new CompoundTag();
            biomeTag.putString("Biome", biome);
            biomeList.add(biomeTag);
        }
        tag.put("VisitedBiomes", biomeList);

        return tag;
    }

    /**
     * Load from NBT.
     */
    public static MissionProgress load(CompoundTag tag) {
        int totalProgress = tag.getInt("TotalProgress");
        int targetProgress = tag.getInt("TargetProgress");
        long defendTicksRemaining = tag.getLong("DefendTicksRemaining");

        Map<UUID, Integer> contributions = new HashMap<>();
        if (tag.contains("PlayerContributions", Tag.TAG_LIST)) {
            ListTag contribList = tag.getList("PlayerContributions", Tag.TAG_COMPOUND);
            for (int i = 0; i < contribList.size(); i++) {
                CompoundTag contribTag = contribList.getCompound(i);
                UUID playerId = contribTag.getUUID("PlayerId");
                int amount = contribTag.getInt("Amount");
                contributions.put(playerId, amount);
            }
        }

        Set<String> biomes = new java.util.HashSet<>();
        if (tag.contains("VisitedBiomes", Tag.TAG_LIST)) {
            ListTag biomeList = tag.getList("VisitedBiomes", Tag.TAG_COMPOUND);
            for (int i = 0; i < biomeList.size(); i++) {
                biomes.add(biomeList.getCompound(i).getString("Biome"));
            }
        }

        return new MissionProgress(totalProgress, targetProgress, contributions, biomes, defendTicksRemaining);
    }
}
