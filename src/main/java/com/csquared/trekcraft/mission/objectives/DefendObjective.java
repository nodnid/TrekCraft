package com.csquared.trekcraft.mission.objectives;

import com.csquared.trekcraft.mission.MissionObjective;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Objective to defend a location for a specified duration.
 * Progress is team effort - equal rewards for completion.
 *
 * @param centerX        X coordinate of the defense center
 * @param centerY        Y coordinate of the defense center
 * @param centerZ        Z coordinate of the defense center
 * @param radius         Radius around center to defend (blocks)
 * @param durationTicks  Duration to defend (in game ticks, 20 ticks = 1 second)
 * @param dimensionKey   The dimension where defense must occur (null for any)
 */
public record DefendObjective(
        int centerX,
        int centerY,
        int centerZ,
        int radius,
        long durationTicks,
        @Nullable String dimensionKey
) implements MissionObjective {

    @Override
    public ObjectiveType getType() {
        return ObjectiveType.DEFEND;
    }

    @Override
    public String getDescription() {
        int minutes = (int) (durationTicks / 1200); // 20 ticks * 60 seconds
        int seconds = (int) ((durationTicks % 1200) / 20);

        String timeStr;
        if (minutes > 0) {
            timeStr = minutes + " minute" + (minutes > 1 ? "s" : "");
            if (seconds > 0) {
                timeStr += " " + seconds + " second" + (seconds > 1 ? "s" : "");
            }
        } else {
            timeStr = seconds + " second" + (seconds > 1 ? "s" : "");
        }

        return "Defend position (" + centerX + ", " + centerY + ", " + centerZ + ") for " + timeStr;
    }

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", getType().name());
        tag.putInt("CenterX", centerX);
        tag.putInt("CenterY", centerY);
        tag.putInt("CenterZ", centerZ);
        tag.putInt("Radius", radius);
        tag.putLong("DurationTicks", durationTicks);
        if (dimensionKey != null) {
            tag.putString("DimensionKey", dimensionKey);
        }
        return tag;
    }

    public static DefendObjective load(CompoundTag tag) {
        int centerX = tag.getInt("CenterX");
        int centerY = tag.getInt("CenterY");
        int centerZ = tag.getInt("CenterZ");
        int radius = tag.getInt("Radius");
        long durationTicks = tag.getLong("DurationTicks");
        String dimensionKey = tag.contains("DimensionKey") ? tag.getString("DimensionKey") : null;
        return new DefendObjective(centerX, centerY, centerZ, radius, durationTicks, dimensionKey);
    }

    /**
     * Get the center position as a BlockPos.
     */
    public BlockPos getCenter() {
        return new BlockPos(centerX, centerY, centerZ);
    }

    /**
     * Check if a position is within the defense area.
     */
    public boolean isWithinArea(BlockPos pos) {
        double distSq = pos.distSqr(getCenter());
        return distSq <= (radius * radius);
    }

    /**
     * Check if a position is within the defense area in the correct dimension.
     */
    public boolean isWithinArea(BlockPos pos, String playerDimension) {
        if (dimensionKey != null && !dimensionKey.equals(playerDimension)) {
            return false;
        }
        return isWithinArea(pos);
    }
}
