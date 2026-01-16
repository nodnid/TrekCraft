package com.csquared.trekcraft.content.block;

import com.csquared.trekcraft.content.blockentity.TransporterPadBlockEntity;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class TransporterPadBlock extends BaseEntityBlock {
    public static final MapCodec<TransporterPadBlock> CODEC = simpleCodec(TransporterPadBlock::new);

    // Slightly shorter than a full block (like a pressure plate but thicker)
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);

    public TransporterPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransporterPadBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TransporterPadBlockEntity padBE) {
                // Use item's custom name if it has one
                Component customName = stack.get(DataComponents.CUSTOM_NAME);
                if (customName != null) {
                    padBE.setPadName(customName.getString());
                } else {
                    // Default name based on position
                    padBE.setPadName("Pad " + pos.getX() + "," + pos.getY() + "," + pos.getZ());
                }

                // Register in SavedData
                TransporterNetworkSavedData.get(serverLevel).registerPad(pos, padBE.getPadName());
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        // Allow renaming with name tag
        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.is(Items.NAME_TAG) && heldItem.has(DataComponents.CUSTOM_NAME)) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof TransporterPadBlockEntity padBE) {
                    String newName = heldItem.get(DataComponents.CUSTOM_NAME).getString();
                    padBE.setPadName(newName);
                    TransporterNetworkSavedData.get(serverLevel).registerPad(pos, newName);

                    player.displayClientMessage(
                            Component.literal("Transporter pad renamed to: " + newName), true);

                    // Consume the name tag
                    if (!player.getAbilities().instabuild) {
                        heldItem.shrink(1);
                    }
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                TransporterNetworkSavedData.get(serverLevel).unregisterPad(pos);
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
