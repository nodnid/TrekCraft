package com.csquared.trekcraft.mission.objectives;

import com.csquared.trekcraft.mission.MissionObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Objective to scan entities or blocks with the tricorder.
 * Progress is team effort - equal rewards for completion.
 *
 * @param entityTypes List of entity types to scan (null/empty for any)
 * @param blockTypes  List of block types to scan (null/empty for any)
 * @param count       Total number of unique scans needed
 */
public record ScanObjective(
        @Nullable List<String> entityTypes,
        @Nullable List<String> blockTypes,
        int count
) implements MissionObjective {

    @Override
    public ObjectiveType getType() {
        return ObjectiveType.SCAN;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder("Scan ");
        sb.append(count);

        if (entityTypes != null && !entityTypes.isEmpty()) {
            if (entityTypes.size() == 1) {
                String name = formatName(entityTypes.get(0));
                sb.append(" ").append(name).append(count > 1 ? "s" : "");
            } else {
                sb.append(" entities (").append(String.join(", ", entityTypes.stream().map(this::formatName).toList())).append(")");
            }
        } else if (blockTypes != null && !blockTypes.isEmpty()) {
            if (blockTypes.size() == 1) {
                String name = formatName(blockTypes.get(0));
                sb.append(" ").append(name).append(" blocks");
            } else {
                sb.append(" blocks (").append(String.join(", ", blockTypes.stream().map(this::formatName).toList())).append(")");
            }
        } else {
            sb.append(" unique targets");
        }

        return sb.toString();
    }

    private String formatName(String resourceLoc) {
        int colonIndex = resourceLoc.indexOf(':');
        String name = colonIndex >= 0 ? resourceLoc.substring(colonIndex + 1) : resourceLoc;
        return name.replace('_', ' ');
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getType().name());

        if (entityTypes != null && !entityTypes.isEmpty()) {
            ListTag entityList = new ListTag();
            for (String entity : entityTypes) {
                entityList.add(StringTag.valueOf(entity));
            }
            tag.put("EntityTypes", entityList);
        }

        if (blockTypes != null && !blockTypes.isEmpty()) {
            ListTag blockList = new ListTag();
            for (String block : blockTypes) {
                blockList.add(StringTag.valueOf(block));
            }
            tag.put("BlockTypes", blockList);
        }

        tag.putInt("Count", count);
        return tag;
    }

    public static ScanObjective load(CompoundTag tag) {
        List<String> entityTypes = null;
        if (tag.contains("EntityTypes", Tag.TAG_LIST)) {
            entityTypes = new ArrayList<>();
            ListTag entityList = tag.getList("EntityTypes", Tag.TAG_STRING);
            for (int i = 0; i < entityList.size(); i++) {
                entityTypes.add(entityList.getString(i));
            }
        }

        List<String> blockTypes = null;
        if (tag.contains("BlockTypes", Tag.TAG_LIST)) {
            blockTypes = new ArrayList<>();
            ListTag blockList = tag.getList("BlockTypes", Tag.TAG_STRING);
            for (int i = 0; i < blockList.size(); i++) {
                blockTypes.add(blockList.getString(i));
            }
        }

        int count = tag.getInt("Count");
        return new ScanObjective(entityTypes, blockTypes, count);
    }

    /**
     * Check if a given entity type matches this objective.
     */
    public boolean matchesEntity(ResourceLocation entityType) {
        if (entityTypes == null || entityTypes.isEmpty()) {
            return blockTypes == null || blockTypes.isEmpty(); // Accept any if no specific types
        }
        return entityTypes.contains(entityType.toString());
    }

    /**
     * Check if a given block type matches this objective.
     */
    public boolean matchesBlock(ResourceLocation blockType) {
        if (blockTypes == null || blockTypes.isEmpty()) {
            return entityTypes == null || entityTypes.isEmpty(); // Accept any if no specific types
        }
        return blockTypes.contains(blockType.toString());
    }
}
