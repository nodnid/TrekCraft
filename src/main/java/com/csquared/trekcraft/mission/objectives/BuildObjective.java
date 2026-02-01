package com.csquared.trekcraft.mission.objectives;

import com.csquared.trekcraft.mission.MissionObjective;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Objective to build a structure from a schematic.
 * Progress is proportional - players are rewarded based on blocks placed.
 *
 * This is a placeholder for the full build mission system with Replicator
 * and CargoBay blocks. For now, it tracks a simple block-placed count.
 *
 * @param schematicName The schematic file name (without path)
 * @param totalBlocks   Total blocks needed (set during mission creation from schematic)
 * @param description   Custom description for this build objective
 */
public record BuildObjective(
        @Nullable String schematicName,
        int totalBlocks,
        @Nullable String description
) implements MissionObjective {

    @Override
    public ObjectiveType getType() {
        return ObjectiveType.BUILD;
    }

    @Override
    public String getDescription() {
        if (description != null && !description.isEmpty()) {
            return description;
        }
        if (schematicName != null && !schematicName.isEmpty()) {
            return "Build " + schematicName.replace(".nbt", "").replace('_', ' ');
        }
        return "Complete construction (" + totalBlocks + " blocks)";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getType().name());
        if (schematicName != null) {
            tag.putString("SchematicName", schematicName);
        }
        tag.putInt("TotalBlocks", totalBlocks);
        if (description != null) {
            tag.putString("Description", description);
        }
        return tag;
    }

    public static BuildObjective load(CompoundTag tag) {
        String schematicName = tag.contains("SchematicName") ? tag.getString("SchematicName") : null;
        int totalBlocks = tag.getInt("TotalBlocks");
        String description = tag.contains("Description") ? tag.getString("Description") : null;
        return new BuildObjective(schematicName, totalBlocks, description);
    }
}
