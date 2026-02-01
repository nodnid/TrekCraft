package com.csquared.trekcraft.mission.objectives;

import com.csquared.trekcraft.mission.MissionObjective;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Objective to contribute items to a shared pool.
 * Progress is proportional - players are rewarded based on contribution.
 * This replaces the old transporter resupply system.
 *
 * @param itemId     The item resource location to contribute (e.g., "trekcraft:latinum_strip")
 * @param targetAmount Total amount needed
 * @param xpPerItem   XP awarded per item contributed
 */
public record ContributionObjective(String itemId, int targetAmount, int xpPerItem) implements MissionObjective {

    @Override
    public ObjectiveType getType() {
        return ObjectiveType.CONTRIBUTION;
    }

    @Override
    public String getDescription() {
        String itemName = itemId;
        int colonIndex = itemId.indexOf(':');
        if (colonIndex >= 0) {
            itemName = itemId.substring(colonIndex + 1);
        }
        itemName = itemName.replace('_', ' ');

        return "Contribute " + targetAmount + " " + itemName + " (" + xpPerItem + " XP each)";
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getType().name());
        tag.putString("ItemId", itemId);
        tag.putInt("TargetAmount", targetAmount);
        tag.putInt("XpPerItem", xpPerItem);
        return tag;
    }

    public static ContributionObjective load(CompoundTag tag) {
        String itemId = tag.getString("ItemId");
        int targetAmount = tag.getInt("TargetAmount");
        int xpPerItem = tag.getInt("XpPerItem");
        return new ContributionObjective(itemId, targetAmount, xpPerItem);
    }

    /**
     * Get the resource location for the item.
     */
    public ResourceLocation getItemLocation() {
        return ResourceLocation.parse(itemId);
    }

    /**
     * Calculate XP reward for a given contribution amount.
     */
    public int calculateXp(int contributedAmount) {
        return contributedAmount * xpPerItem;
    }
}
