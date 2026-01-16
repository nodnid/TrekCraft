package com.csquared.trekcraft.content.block;

import com.csquared.trekcraft.content.blockentity.TransporterRoomBlockEntity;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TransporterRoomBlock extends BaseEntityBlock {
    public static final MapCodec<TransporterRoomBlock> CODEC = simpleCodec(TransporterRoomBlock::new);

    public TransporterRoomBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransporterRoomBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // Check Overworld only
            if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
                // Drop the item back and remove the block
                level.removeBlock(pos, false);
                if (placer instanceof Player player) {
                    if (!player.getAbilities().instabuild) {
                        player.addItem(stack.copy());
                    }
                    player.displayClientMessage(
                            Component.literal("Transporter Room can only be placed in the Overworld."), false);
                }
                return;
            }

            TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);

            // Check one-per-world rule
            if (data.hasTransporterRoom()) {
                BlockPos existingPos = data.getTransporterRoomPos();
                // Check if the existing room is still there
                if (serverLevel.getBlockEntity(existingPos) instanceof TransporterRoomBlockEntity) {
                    // Room already exists, deny placement
                    level.removeBlock(pos, false);
                    if (placer instanceof Player player) {
                        if (!player.getAbilities().instabuild) {
                            player.addItem(stack.copy());
                        }
                        player.displayClientMessage(
                                Component.literal("There is only one Transporter Room per world. Existing room at: " +
                                        existingPos.getX() + ", " + existingPos.getY() + ", " + existingPos.getZ()), false);
                    }
                    return;
                }
                // Old room no longer exists, clear it
                data.clearTransporterRoom();
            }

            // Register this as the new transporter room
            data.setTransporterRoom(pos);

            if (placer instanceof Player player) {
                player.displayClientMessage(
                        Component.literal("Transporter Room online. Transport system activated."), false);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TransporterRoomBlockEntity roomBE) {
                // Open the fuel container menu
                player.openMenu(roomBE);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TransporterRoomBlockEntity roomBE) {
                // Drop inventory contents
                Containers.dropContents(level, pos, roomBE);
            }

            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
                if (data.getTransporterRoomPos() != null && data.getTransporterRoomPos().equals(pos)) {
                    data.clearTransporterRoom();
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.TRANSPORTER_ROOM.get(),
                TransporterRoomBlockEntity::serverTick);
    }
}
