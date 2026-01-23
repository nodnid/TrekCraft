package com.csquared.trekcraft.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Utility class for detecting and validating wormhole portal frames.
 * The frame must be made of cobblestone and form a rectangular shape
 * with an air interior, similar to a nether portal.
 */
public class WormholeFrameDetector {

    public static final int MIN_WIDTH = 2;  // Interior width (matches nether portal)
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3; // Interior height (matches nether portal)
    public static final int MAX_HEIGHT = 21;

    /**
     * Result of frame detection containing all relevant data.
     */
    public record FrameResult(
            boolean isValid,
            BlockPos anchorPos,      // Bottom-left interior block (for consistent reference)
            Direction.Axis axis,
            int width,               // Interior width
            int height,              // Interior height
            List<BlockPos> frameBlocks,
            List<BlockPos> interiorBlocks,
            String errorMessage
    ) {
        public static FrameResult invalid(String reason) {
            return new FrameResult(false, BlockPos.ZERO, Direction.Axis.X, 0, 0,
                    List.of(), List.of(), reason);
        }

        public static FrameResult valid(BlockPos anchorPos, Direction.Axis axis, int width, int height,
                                        List<BlockPos> frameBlocks, List<BlockPos> interiorBlocks) {
            return new FrameResult(true, anchorPos, axis, width, height,
                    frameBlocks, interiorBlocks, null);
        }
    }

    /**
     * Detect a valid portal frame from a clicked position.
     * The clicked position should be on a cobblestone block that's part of the frame.
     */
    public static FrameResult detectFrame(Level level, BlockPos clickedPos) {
        BlockState clickedState = level.getBlockState(clickedPos);

        // Must click on cobblestone
        if (!clickedState.is(Blocks.COBBLESTONE)) {
            return FrameResult.invalid("Must click on cobblestone");
        }

        // Try both axes (X-aligned portal or Z-aligned portal)
        FrameResult xResult = tryDetectFrame(level, clickedPos, Direction.Axis.X);
        if (xResult.isValid()) {
            return xResult;
        }

        FrameResult zResult = tryDetectFrame(level, clickedPos, Direction.Axis.Z);
        if (zResult.isValid()) {
            return zResult;
        }

        // Return the more informative error
        if (xResult.errorMessage() != null && !xResult.errorMessage().equals("Must click on cobblestone")) {
            return xResult;
        }
        return zResult.errorMessage() != null ? zResult : FrameResult.invalid("No valid portal frame found");
    }

    /**
     * Try to detect a frame with the given axis orientation.
     */
    private static FrameResult tryDetectFrame(Level level, BlockPos clickedPos, Direction.Axis axis) {
        // Determine horizontal direction based on axis
        Direction horizontal = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        Direction vertical = Direction.UP;

        // First, find the bottom-left corner of the frame
        // Start from clicked position and search down and in the negative horizontal direction
        BlockPos searchPos = clickedPos;

        // Search down to find the bottom of the frame
        while (isCobblestone(level, searchPos.below())) {
            searchPos = searchPos.below();
        }

        // Search in negative horizontal direction to find left side
        Direction negHorizontal = horizontal.getOpposite();
        while (isCobblestone(level, searchPos.relative(negHorizontal))) {
            searchPos = searchPos.relative(negHorizontal);
        }

        // Now searchPos should be at the bottom-left corner of the frame
        BlockPos bottomLeftCorner = searchPos;

        // Find the width by scanning right along the bottom
        int bottomWidth = 0;
        BlockPos scanPos = bottomLeftCorner;
        while (isCobblestone(level, scanPos)) {
            bottomWidth++;
            scanPos = scanPos.relative(horizontal);
            if (bottomWidth > MAX_WIDTH + 2) {
                return FrameResult.invalid("Frame too wide");
            }
        }

        // Interior width is bottomWidth - 2 (excluding the two corner blocks)
        int interiorWidth = bottomWidth - 2;
        if (interiorWidth < MIN_WIDTH) {
            return FrameResult.invalid("Frame too narrow (min " + MIN_WIDTH + " interior width)");
        }
        if (interiorWidth > MAX_WIDTH) {
            return FrameResult.invalid("Frame too wide (max " + MAX_WIDTH + " interior width)");
        }

        // Find the height by scanning up along the left side
        int leftHeight = 0;
        scanPos = bottomLeftCorner;
        while (isCobblestone(level, scanPos)) {
            leftHeight++;
            scanPos = scanPos.above();
            if (leftHeight > MAX_HEIGHT + 2) {
                return FrameResult.invalid("Frame too tall");
            }
        }

        // Interior height is leftHeight - 2 (excluding top and bottom)
        int interiorHeight = leftHeight - 2;
        if (interiorHeight < MIN_HEIGHT) {
            return FrameResult.invalid("Frame too short (min " + MIN_HEIGHT + " interior height)");
        }
        if (interiorHeight > MAX_HEIGHT) {
            return FrameResult.invalid("Frame too tall (max " + MAX_HEIGHT + " interior height)");
        }

        // Now validate the complete frame and collect block positions
        List<BlockPos> frameBlocks = new ArrayList<>();
        List<BlockPos> interiorBlocks = new ArrayList<>();

        // Validate and collect bottom row (including corners)
        for (int i = 0; i < bottomWidth; i++) {
            BlockPos pos = bottomLeftCorner.relative(horizontal, i);
            if (!isCobblestone(level, pos)) {
                return FrameResult.invalid("Incomplete bottom row");
            }
            frameBlocks.add(pos);
        }

        // Validate and collect top row
        for (int i = 0; i < bottomWidth; i++) {
            BlockPos pos = bottomLeftCorner.above(leftHeight - 1).relative(horizontal, i);
            if (!isCobblestone(level, pos)) {
                return FrameResult.invalid("Incomplete top row");
            }
            frameBlocks.add(pos);
        }

        // Validate and collect left side (excluding corners already added)
        for (int i = 1; i < leftHeight - 1; i++) {
            BlockPos pos = bottomLeftCorner.above(i);
            if (!isCobblestone(level, pos)) {
                return FrameResult.invalid("Incomplete left side");
            }
            frameBlocks.add(pos);
        }

        // Validate and collect right side (excluding corners)
        BlockPos bottomRightCorner = bottomLeftCorner.relative(horizontal, bottomWidth - 1);
        for (int i = 1; i < leftHeight - 1; i++) {
            BlockPos pos = bottomRightCorner.above(i);
            if (!isCobblestone(level, pos)) {
                return FrameResult.invalid("Incomplete right side");
            }
            frameBlocks.add(pos);
        }

        // Validate interior is all air and collect positions
        // Anchor is the bottom-left interior block (one up and one right from bottom-left corner)
        BlockPos anchorPos = bottomLeftCorner.above(1).relative(horizontal, 1);

        for (int y = 0; y < interiorHeight; y++) {
            for (int x = 0; x < interiorWidth; x++) {
                BlockPos interiorPos = anchorPos.above(y).relative(horizontal, x);
                BlockState state = level.getBlockState(interiorPos);
                if (!state.isAir() && !state.is(Blocks.FIRE)) {
                    return FrameResult.invalid("Interior must be empty");
                }
                interiorBlocks.add(interiorPos);
            }
        }

        return FrameResult.valid(anchorPos, axis, interiorWidth, interiorHeight, frameBlocks, interiorBlocks);
    }

    /**
     * Check if a position contains cobblestone.
     */
    private static boolean isCobblestone(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(Blocks.COBBLESTONE);
    }

    /**
     * Check if an existing portal frame is still valid (for destruction detection).
     */
    public static boolean isFrameStillValid(Level level, BlockPos anchorPos, Direction.Axis axis, int width, int height) {
        Direction horizontal = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;

        // Check bottom row (one below anchor, from one left of anchor to width + 1 right)
        BlockPos bottomLeft = anchorPos.below().relative(horizontal.getOpposite());
        for (int i = 0; i < width + 2; i++) {
            if (!isCobblestone(level, bottomLeft.relative(horizontal, i))) {
                return false;
            }
        }

        // Check top row
        BlockPos topLeft = bottomLeft.above(height + 1);
        for (int i = 0; i < width + 2; i++) {
            if (!isCobblestone(level, topLeft.relative(horizontal, i))) {
                return false;
            }
        }

        // Check left side
        for (int i = 1; i <= height; i++) {
            if (!isCobblestone(level, bottomLeft.above(i))) {
                return false;
            }
        }

        // Check right side
        BlockPos bottomRight = bottomLeft.relative(horizontal, width + 1);
        for (int i = 1; i <= height; i++) {
            if (!isCobblestone(level, bottomRight.above(i))) {
                return false;
            }
        }

        return true;
    }
}
