package com.csquared.trekcraft.mission.objectives;

import com.csquared.trekcraft.mission.MissionObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Objective to kill a specific number of entities.
 * Progress is proportional - players are rewarded based on kills.
 *
 * @param entityType The entity type resource location (e.g., "minecraft:zombie"), or null for any hostile
 * @param count      The total number of kills needed
 */
public record KillObjective(@Nullable String entityType, int count) implements MissionObjective {

    @Override
    public ObjectiveType getType() {
        return ObjectiveType.KILL;
    }

    @Override
    public String getDescription() {
        if (entityType == null || entityType.isEmpty()) {
            return "Kill " + count + " hostile mobs";
        }

        // Extract entity name from resource location
        String entityName = entityType;
        int colonIndex = entityType.indexOf(':');
        if (colonIndex >= 0) {
            entityName = entityType.substring(colonIndex + 1);
        }
        // Convert snake_case to Title Case
        entityName = entityName.replace('_', ' ');

        // Handle pluralization simply
        String suffix = count == 1 ? "" : "s";
        return "Kill " + count + " " + entityName + suffix;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getType().name());
        if (entityType != null) {
            tag.putString("EntityType", entityType);
        }
        tag.putInt("Count", count);
        return tag;
    }

    public static KillObjective load(CompoundTag tag) {
        String entityType = tag.contains("EntityType") ? tag.getString("EntityType") : null;
        int count = tag.getInt("Count");
        return new KillObjective(entityType, count);
    }

    /**
     * Get the resource location for the entity type, or null if any hostile.
     */
    @Nullable
    public ResourceLocation getEntityTypeLocation() {
        return entityType != null ? ResourceLocation.parse(entityType) : null;
    }

    /**
     * Check if this objective accepts any hostile mob.
     */
    public boolean isAnyHostile() {
        return entityType == null || entityType.isEmpty();
    }
}
