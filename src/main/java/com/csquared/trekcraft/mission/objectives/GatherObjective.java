package com.csquared.trekcraft.mission.objectives;

import com.csquared.trekcraft.mission.MissionObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Objective to gather a specific quantity of items.
 * Progress is proportional - players are rewarded based on contribution.
 *
 * @param itemId   The item resource location (e.g., "minecraft:iron_ingot")
 * @param quantity The total number of items needed
 */
public record GatherObjective(String itemId, int quantity) implements MissionObjective {

    @Override
    public ObjectiveType getType() {
        return ObjectiveType.GATHER;
    }

    @Override
    public String getDescription() {
        // Extract item name from resource location
        String itemName = itemId;
        int colonIndex = itemId.indexOf(':');
        if (colonIndex >= 0) {
            itemName = itemId.substring(colonIndex + 1);
        }
        // Convert snake_case to Title Case
        itemName = itemName.replace('_', ' ');
        return "Gather " + quantity + " " + itemName;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getType().name());
        tag.putString("ItemId", itemId);
        tag.putInt("Quantity", quantity);
        return tag;
    }

    public static GatherObjective load(CompoundTag tag) {
        String itemId = tag.getString("ItemId");
        int quantity = tag.getInt("Quantity");
        return new GatherObjective(itemId, quantity);
    }

    /**
     * Get the resource location for the item.
     */
    public ResourceLocation getItemLocation() {
        return ResourceLocation.parse(itemId);
    }
}
