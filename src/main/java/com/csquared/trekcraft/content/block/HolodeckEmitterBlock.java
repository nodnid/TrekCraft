package com.csquared.trekcraft.content.block;

import com.csquared.trekcraft.content.blockentity.HolodeckControllerBlockEntity;
import com.csquared.trekcraft.registry.ModBlocks;
import com.csquared.trekcraft.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

/**
 * Holodeck emitter block that forms the frame structure of a holodeck.
 * When connected to an active controller, emitters change to ACTIVE state and emit light.
 */
public class HolodeckEmitterBlock extends Block {
    public static final MapCodec<HolodeckEmitterBlock> CODEC = simpleCodec(HolodeckEmitterBlock::new);

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public HolodeckEmitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(ACTIVE) ? 15 : 0;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Only allow breaking with a tricorder - instant break
        if (player.getMainHandItem().is(ModItems.TRICORDER.get())) {
            return 1.0f;
        }
        return 0.0f;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        // If this emitter was part of an active holodeck, deactivate it
        if (!state.is(newState.getBlock()) && state.getValue(ACTIVE)) {
            if (!level.isClientSide) {
                findAndDeactivateController(level, pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * Search for and deactivate any holodeck controller connected to this emitter.
     */
    private void findAndDeactivateController(Level level, BlockPos brokenEmitterPos) {
        // Start from neighbors of the broken emitter (since the emitter itself is gone)
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();

        // Add all neighbors of the broken position as starting points
        queue.add(brokenEmitterPos.north());
        queue.add(brokenEmitterPos.south());
        queue.add(brokenEmitterPos.east());
        queue.add(brokenEmitterPos.west());
        queue.add(brokenEmitterPos.above());
        queue.add(brokenEmitterPos.below());

        // Mark the broken position as visited so we don't revisit it
        visited.add(brokenEmitterPos);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);

            BlockState state = level.getBlockState(current);

            // Check if this is the controller
            if (state.is(ModBlocks.HOLODECK_CONTROLLER.get())) {
                BlockEntity be = level.getBlockEntity(current);
                if (be instanceof HolodeckControllerBlockEntity controller) {
                    controller.deactivate();
                }
                return; // Found and deactivated the controller
            }

            // If this is an active emitter, continue searching neighbors
            if (state.is(ModBlocks.HOLODECK_EMITTER.get()) && state.getValue(ACTIVE)) {
                queue.add(current.north());
                queue.add(current.south());
                queue.add(current.east());
                queue.add(current.west());
                queue.add(current.above());
                queue.add(current.below());
            }
        }
    }
}
