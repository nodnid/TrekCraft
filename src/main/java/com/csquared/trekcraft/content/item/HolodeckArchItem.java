package com.csquared.trekcraft.content.item;

import com.csquared.trekcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Custom BlockItem for the Holodeck Controller that auto-places a 4x3 arch structure.
 *
 * When player clicks at bottom-left corner (facing the wall from outside):
 *
 * [E] [E] [E] [E]     <- y=2: 4 emitters
 * [C] [D] [D] [E]     <- y=1: controller, 2 door tops (air), emitter
 * [E] [D] [D] [E]     <- y=0: emitter, 2 door bottoms (air), emitter (CLICK HERE)
 *
 * C = Controller (placed at y=1, not y=0)
 * E = Emitter (7 total)
 * D = Air (player manually places doors here)
 */
public class HolodeckArchItem extends BlockItem {

    public HolodeckArchItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockPos clickPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();

        // If clicking on a block, offset to the adjacent position
        if (!level.getBlockState(clickPos).canBeReplaced(context)) {
            clickPos = clickPos.relative(clickedFace);
        }

        // Get player's horizontal facing direction (the direction they're looking)
        Direction playerFacing = context.getHorizontalDirection();

        // Calculate "right" direction (perpendicular to facing, to the player's right)
        // When player faces NORTH, right is EAST
        // When player faces SOUTH, right is WEST
        // When player faces EAST, right is SOUTH
        // When player faces WEST, right is NORTH
        Direction rightDir = playerFacing.getClockWise();

        // Calculate all 12 positions for the 4x3 arch relative to click position
        // Click position is at the bottom-left corner (where the bottom-left emitter goes)
        BlockPos[][] archPositions = calculateArchPositions(clickPos, rightDir);

        // Validate all positions are replaceable
        if (!validatePositions(level, context, archPositions)) {
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(
                    Component.literal("Not enough space for holodeck arch (requires 4x3 area)."), true);
            }
            return InteractionResult.FAIL;
        }

        // Place the structure - emitters first, then controller
        placeArchStructure(level, archPositions, playerFacing);

        // Consume one item from the stack
        if (context.getPlayer() != null && !context.getPlayer().getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }

        // Send success message
        if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(
                Component.literal("Holodeck arch placed! Add doors to the entrance and build emitter walls to activate."), true);
        }

        return InteractionResult.CONSUME;
    }

    /**
     * Calculate all 12 positions for the 4x3 arch structure.
     * Returns a 3x4 array where [row][col] represents the grid.
     * Row 0 = y+0 (ground level), Row 1 = y+1, Row 2 = y+2
     * Col 0 = leftmost, Col 3 = rightmost
     */
    private BlockPos[][] calculateArchPositions(BlockPos clickPos, Direction rightDir) {
        BlockPos[][] positions = new BlockPos[3][4];

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                // Start at clickPos and offset by row (vertical) and col (horizontal in rightDir)
                positions[row][col] = clickPos
                    .above(row)
                    .relative(rightDir, col);
            }
        }

        return positions;
    }

    /**
     * Validate that all positions are replaceable (air, water, etc.)
     */
    private boolean validatePositions(Level level, BlockPlaceContext context, BlockPos[][] positions) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                BlockPos pos = positions[row][col];
                BlockState existing = level.getBlockState(pos);

                // Check if the block can be replaced
                if (!existing.canBeReplaced(context)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Place the arch structure.
     *
     * Layout (looking from outside, rightDir goes left to right):
     * [E] [E] [E] [E]     <- y=2: 4 emitters (row 2)
     * [C] [D] [D] [E]     <- y=1: controller, 2 door spaces, emitter (row 1)
     * [E] [D] [D] [E]     <- y=0: emitter, 2 door spaces, emitter (row 0)
     */
    private void placeArchStructure(Level level, BlockPos[][] positions, Direction playerFacing) {
        BlockState emitterState = ModBlocks.HOLODECK_EMITTER.get().defaultBlockState();
        BlockState controllerState = ModBlocks.HOLODECK_CONTROLLER.get().defaultBlockState();

        // Set controller facing (opposite of player facing, so it faces the player)
        controllerState = controllerState.setValue(
            com.csquared.trekcraft.content.block.HolodeckControllerBlock.FACING,
            playerFacing.getOpposite());

        // Row 0 (ground level): E, D, D, E
        level.setBlock(positions[0][0], emitterState, 3);  // Left emitter
        // positions[0][1] and [0][2] are door spaces - leave as air
        level.setBlock(positions[0][3], emitterState, 3);  // Right emitter

        // Row 1 (y+1): C, D, D, E
        level.setBlock(positions[1][0], controllerState, 3);  // Controller
        // positions[1][1] and [1][2] are door spaces - leave as air
        level.setBlock(positions[1][3], emitterState, 3);  // Right emitter

        // Row 2 (y+2): E, E, E, E
        level.setBlock(positions[2][0], emitterState, 3);
        level.setBlock(positions[2][1], emitterState, 3);
        level.setBlock(positions[2][2], emitterState, 3);
        level.setBlock(positions[2][3], emitterState, 3);
    }
}
