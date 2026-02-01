package com.csquared.trekcraft.mission.objectives;

import com.csquared.trekcraft.mission.MissionObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Objective that combines multiple sub-objectives.
 * All sub-objectives must be completed for this objective to complete.
 * Progress tracking depends on sub-objective types.
 *
 * @param objectives List of sub-objectives
 * @param description Custom description for this composite
 */
public record CompositeObjective(List<MissionObjective> objectives, String description) implements MissionObjective {

    @Override
    public ObjectiveType getType() {
        return ObjectiveType.COMPOSITE;
    }

    @Override
    public String getDescription() {
        if (description != null && !description.isEmpty()) {
            return description;
        }
        return "Complete " + objectives.size() + " objectives";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getType().name());

        ListTag objectiveList = new ListTag();
        for (MissionObjective objective : objectives) {
            objectiveList.add(objective.save());
        }
        tag.put("Objectives", objectiveList);

        if (description != null) {
            tag.putString("Description", description);
        }
        return tag;
    }

    public static CompositeObjective load(CompoundTag tag) {
        List<MissionObjective> objectives = new ArrayList<>();
        if (tag.contains("Objectives", Tag.TAG_LIST)) {
            ListTag objectiveList = tag.getList("Objectives", Tag.TAG_COMPOUND);
            for (int i = 0; i < objectiveList.size(); i++) {
                MissionObjective obj = MissionObjective.load(objectiveList.getCompound(i));
                if (obj != null) {
                    objectives.add(obj);
                }
            }
        }

        String description = tag.contains("Description") ? tag.getString("Description") : null;
        return new CompositeObjective(objectives, description);
    }

    /**
     * Get a list of objective descriptions for display.
     */
    public List<String> getSubObjectiveDescriptions() {
        return objectives.stream()
                .map(MissionObjective::getDescription)
                .toList();
    }
}
