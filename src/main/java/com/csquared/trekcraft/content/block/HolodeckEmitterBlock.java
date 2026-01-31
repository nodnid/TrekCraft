package com.csquared.trekcraft.content.block;

import com.csquared.trekcraft.registry.ModItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

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
        return state.getValue(ACTIVE) ? 7 : 0;
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        // Only allow breaking with a tricorder - instant break
        if (player.getMainHandItem().is(ModItems.TRICORDER.get())) {
            return 1.0f;
        }
        return 0.0f;
    }
}
