package com.csquared.trekcraft.mission;

import net.minecraft.nbt.CompoundTag;

/**
 * Interface for mission objectives.
 * Each objective type defines what needs to be accomplished.
 */
public interface MissionObjective {

    /**
     * Get the type identifier for this objective.
     */
    ObjectiveType getType();

    /**
     * Get a human-readable description of this objective.
     */
    String getDescription();

    /**
     * Save this objective to NBT.
     */
    CompoundTag save();

    /**
     * Objective types for serialization.
     */
    enum ObjectiveType {
        GATHER,
        KILL,
        SCAN,
        EXPLORE,
        DEFEND,
        CONTRIBUTION,
        BUILD,
        COMPOSITE
    }

    /**
     * Load an objective from NBT.
     */
    static MissionObjective load(CompoundTag tag) {
        String typeStr = tag.getString("Type");
        ObjectiveType type;
        try {
            type = ObjectiveType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return null;
        }

        return switch (type) {
            case GATHER -> com.csquared.trekcraft.mission.objectives.GatherObjective.load(tag);
            case KILL -> com.csquared.trekcraft.mission.objectives.KillObjective.load(tag);
            case SCAN -> com.csquared.trekcraft.mission.objectives.ScanObjective.load(tag);
            case EXPLORE -> com.csquared.trekcraft.mission.objectives.ExploreObjective.load(tag);
            case DEFEND -> com.csquared.trekcraft.mission.objectives.DefendObjective.load(tag);
            case CONTRIBUTION -> com.csquared.trekcraft.mission.objectives.ContributionObjective.load(tag);
            case BUILD -> com.csquared.trekcraft.mission.objectives.BuildObjective.load(tag);
            case COMPOSITE -> com.csquared.trekcraft.mission.objectives.CompositeObjective.load(tag);
        };
    }
}
