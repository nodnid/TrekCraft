package com.csquared.trekcraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

public class SafeTeleportFinder {
    private static final int DEFAULT_RADIUS = 5;
    private static final int DEFAULT_VERTICAL_RANGE = 3;

    public static Optional<BlockPos> findSafeSpot(Level level, BlockPos anchor) {
        return findSafeSpot(level, anchor, DEFAULT_RADIUS, DEFAULT_VERTICAL_RANGE);
    }

    public static Optional<BlockPos> findSafeSpot(Level level, BlockPos anchor, int radius, int verticalRange) {
        // First, check if the anchor position itself is safe
        if (isSafePosition(level, anchor)) {
            return Optional.of(anchor);
        }

        // Spiral outward search
        for (int r = 1; r <= radius; r++) {
            for (int dy = 0; dy <= verticalRange; dy++) {
                // Check both above and below
                for (int vertDir = -1; vertDir <= 1; vertDir += 2) {
                    int actualDy = dy * vertDir;
                    if (dy == 0 && vertDir == 1) continue; // Skip duplicate at dy=0

                    // Search the perimeter of the current radius
                    for (int dx = -r; dx <= r; dx++) {
                        for (int dz = -r; dz <= r; dz++) {
                            // Only check the perimeter, not the filled square
                            if (Math.abs(dx) == r || Math.abs(dz) == r) {
                                BlockPos checkPos = anchor.offset(dx, actualDy, dz);
                                if (isSafePosition(level, checkPos)) {
                                    return Optional.of(checkPos);
                                }
                            }
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    public static boolean isSafePosition(Level level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockPos at = pos;
        BlockPos above = pos.above();

        // Check solid ground below
        BlockState groundState = level.getBlockState(below);
        if (!groundState.isSolidRender(level, below)) {
            return false;
        }

        // Check 2-block high space is clear
        BlockState atState = level.getBlockState(at);
        BlockState aboveState = level.getBlockState(above);

        if (!isPassable(level, at, atState) || !isPassable(level, above, aboveState)) {
            return false;
        }

        // Check for hazardous blocks
        if (isHazardous(groundState) || isHazardous(atState) || isHazardous(aboveState)) {
            return false;
        }

        // Check block below feet isn't hazardous
        BlockState twoBelowState = level.getBlockState(below.below());
        if (twoBelowState.is(Blocks.LAVA) || twoBelowState.is(Blocks.FIRE)) {
            return false;
        }

        return true;
    }

    private static boolean isPassable(Level level, BlockPos pos, BlockState state) {
        // Air and non-collidable blocks are passable
        if (state.isAir()) {
            return true;
        }
        // Check if the block has no collision
        return state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean isHazardous(BlockState state) {
        // Check for dangerous blocks
        if (state.is(Blocks.LAVA)) return true;
        if (state.is(Blocks.FIRE)) return true;
        if (state.is(Blocks.SOUL_FIRE)) return true;
        if (state.is(Blocks.CACTUS)) return true;
        if (state.is(Blocks.SWEET_BERRY_BUSH)) return true;
        if (state.is(Blocks.WITHER_ROSE)) return true;
        if (state.is(Blocks.MAGMA_BLOCK)) return true;
        if (state.is(Blocks.CAMPFIRE)) return true;
        if (state.is(Blocks.SOUL_CAMPFIRE)) return true;
        if (state.is(BlockTags.FIRE)) return true;

        return false;
    }
}
