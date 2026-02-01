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
 * Objective to visit specific biomes or locations.
 * Progress is team effort - equal rewards for completion.
 *
 * @param biomes List of biome resource locations to visit (null for any)
 * @param count  Total number of unique biomes to visit
 */
public record ExploreObjective(@Nullable List<String> biomes, int count) implements MissionObjective {

    @Override
    public ObjectiveType getType() {
        return ObjectiveType.EXPLORE;
    }

    @Override
    public String getDescription() {
        if (biomes != null && !biomes.isEmpty()) {
            if (biomes.size() == 1) {
                return "Visit the " + formatName(biomes.get(0)) + " biome";
            }
            return "Visit biomes: " + String.join(", ", biomes.stream().map(this::formatName).toList());
        }
        return "Explore " + count + " unique biome" + (count > 1 ? "s" : "");
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

        if (biomes != null && !biomes.isEmpty()) {
            ListTag biomeList = new ListTag();
            for (String biome : biomes) {
                biomeList.add(StringTag.valueOf(biome));
            }
            tag.put("Biomes", biomeList);
        }

        tag.putInt("Count", count);
        return tag;
    }

    public static ExploreObjective load(CompoundTag tag) {
        List<String> biomes = null;
        if (tag.contains("Biomes", Tag.TAG_LIST)) {
            biomes = new ArrayList<>();
            ListTag biomeList = tag.getList("Biomes", Tag.TAG_STRING);
            for (int i = 0; i < biomeList.size(); i++) {
                biomes.add(biomeList.getString(i));
            }
        }

        int count = tag.getInt("Count");
        return new ExploreObjective(biomes, count);
    }

    /**
     * Check if a given biome matches this objective.
     */
    public boolean matchesBiome(ResourceLocation biome) {
        if (biomes == null || biomes.isEmpty()) {
            return true; // Accept any biome when no specific list
        }
        return biomes.contains(biome.toString());
    }

    /**
     * Check if this objective requires specific biomes.
     */
    public boolean hasSpecificBiomes() {
        return biomes != null && !biomes.isEmpty();
    }
}
