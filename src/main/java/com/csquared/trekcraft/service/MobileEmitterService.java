package com.csquared.trekcraft.service;

import com.csquared.trekcraft.TrekCraftConfig;
import com.csquared.trekcraft.content.blockentity.MobileEmitterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;

/**
 * Service for validating and managing mobile emitter networks.
 * Four emitters at corners of a rectangle on the XZ plane form a holo-area
 * extending from emitter Y level up to the configured max height.
 */
public class MobileEmitterService {

    public enum ValidationResult {
        VALID,
        NOT_ENOUGH_EMITTERS,      // Less than 4 emitters
        TOO_MANY_EMITTERS,        // More than 4 emitters
        NOT_SAME_Y_LEVEL,         // Emitters at different Y levels
        NOT_RECTANGULAR,          // Doesn't form a valid rectangle
        EXCEEDS_MAX_X,            // X dimension exceeds config limit
        EXCEEDS_MAX_Z,            // Z dimension exceeds config limit
        EMITTER_NOT_FOUND,        // A linked position no longer has an emitter
        NETWORK_MISMATCH          // Emitters have mismatched network IDs
    }

    /**
     * Validate a complete network of 4 emitters.
     * @param level The server level
     * @param corners The 4 corner positions
     * @param networkId The network UUID to verify
     * @return ValidationResult indicating success or specific failure reason
     */
    public static ValidationResult validateNetwork(ServerLevel level, List<BlockPos> corners, UUID networkId) {
        // Check emitter count
        if (corners.size() < 4) {
            return ValidationResult.NOT_ENOUGH_EMITTERS;
        }
        if (corners.size() > 4) {
            return ValidationResult.TOO_MANY_EMITTERS;
        }

        // Verify all positions have emitters with matching network IDs
        for (BlockPos pos : corners) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MobileEmitterBlockEntity emitter)) {
                return ValidationResult.EMITTER_NOT_FOUND;
            }
            if (!networkId.equals(emitter.getNetworkId())) {
                return ValidationResult.NETWORK_MISMATCH;
            }
        }

        // Check all same Y level
        if (!isFlatPlane(corners)) {
            return ValidationResult.NOT_SAME_Y_LEVEL;
        }

        // Check forms valid rectangle
        if (!isValidRectangle(corners)) {
            return ValidationResult.NOT_RECTANGULAR;
        }

        // Check size within limits
        ValidationResult sizeResult = checkSizeWithinLimits(corners, level);
        if (sizeResult != ValidationResult.VALID) {
            return sizeResult;
        }

        return ValidationResult.VALID;
    }

    /**
     * Check if all corners are at the same Y level.
     */
    public static boolean isFlatPlane(List<BlockPos> corners) {
        if (corners.isEmpty()) return false;
        int y = corners.get(0).getY();
        for (BlockPos pos : corners) {
            if (pos.getY() != y) {
                return false;
            }
        }
        return true;
    }

    /**
     * Check if 4 positions form a valid axis-aligned rectangle.
     * Must have exactly 2 unique X values and 2 unique Z values,
     * and all 4 combinations must be present.
     */
    public static boolean isValidRectangle(List<BlockPos> corners) {
        if (corners.size() != 4) return false;

        Set<Integer> xValues = new HashSet<>();
        Set<Integer> zValues = new HashSet<>();

        for (BlockPos pos : corners) {
            xValues.add(pos.getX());
            zValues.add(pos.getZ());
        }

        // Must have exactly 2 unique X and 2 unique Z values
        if (xValues.size() != 2 || zValues.size() != 2) {
            return false;
        }

        // Check all 4 combinations exist
        Set<String> expectedCombinations = new HashSet<>();
        for (int x : xValues) {
            for (int z : zValues) {
                expectedCombinations.add(x + "," + z);
            }
        }

        Set<String> actualCombinations = new HashSet<>();
        for (BlockPos pos : corners) {
            actualCombinations.add(pos.getX() + "," + pos.getZ());
        }

        return expectedCombinations.equals(actualCombinations);
    }

    /**
     * Check if the area defined by corners is within configured size limits.
     */
    public static ValidationResult checkSizeWithinLimits(List<BlockPos> corners, Level level) {
        if (corners.size() != 4) return ValidationResult.NOT_RECTANGULAR;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : corners) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        int sizeX = maxX - minX + 1;
        int sizeZ = maxZ - minZ + 1;

        if (sizeX > TrekCraftConfig.mobileEmitterMaxX) {
            return ValidationResult.EXCEEDS_MAX_X;
        }
        if (sizeZ > TrekCraftConfig.mobileEmitterMaxZ) {
            return ValidationResult.EXCEEDS_MAX_Z;
        }

        return ValidationResult.VALID;
    }

    /**
     * Calculate the interior bounds from 4 corner positions.
     * Interior is the XZ area between corners, Y extends from emitter level up to configured max height.
     * @return [interiorMin, interiorMax] or null if corners are invalid
     */
    public static BlockPos[] calculateInteriorBounds(List<BlockPos> corners, Level level) {
        if (corners.size() != 4) return null;

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int y = corners.get(0).getY();

        for (BlockPos pos : corners) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // Interior is the area between emitters
        // Y extends from emitter level up to configured max height (capped at world height)
        int maxY = Math.min(y + TrekCraftConfig.mobileEmitterMaxY - 1, level.getMaxBuildHeight() - 1);
        BlockPos interiorMin = new BlockPos(minX, y, minZ);
        BlockPos interiorMax = new BlockPos(maxX, maxY, maxZ);

        return new BlockPos[]{interiorMin, interiorMax};
    }

    /**
     * Get a human-readable error message for a validation result.
     */
    public static String getErrorMessage(ValidationResult result) {
        return switch (result) {
            case VALID -> "Network is valid and ready to activate.";
            case NOT_ENOUGH_EMITTERS -> "Need 4 emitters to form a complete network.";
            case TOO_MANY_EMITTERS -> "Network has more than 4 emitters.";
            case NOT_SAME_Y_LEVEL -> "All 4 emitters must be at the same Y level.";
            case NOT_RECTANGULAR -> "Emitters must form a rectangle on the XZ plane.";
            case EXCEEDS_MAX_X -> "Area exceeds maximum X size of " + TrekCraftConfig.mobileEmitterMaxX + " blocks.";
            case EXCEEDS_MAX_Z -> "Area exceeds maximum Z size of " + TrekCraftConfig.mobileEmitterMaxZ + " blocks.";
            case EMITTER_NOT_FOUND -> "One or more linked emitter positions no longer contain emitters.";
            case NETWORK_MISMATCH -> "Emitters have mismatched network IDs.";
        };
    }
}
