package com.csquared.trekcraft.content.block;

import com.csquared.trekcraft.content.blockentity.WormholePortalBlockEntity;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.WormholeRecord;
import com.csquared.trekcraft.service.WormholeService;
import com.csquared.trekcraft.util.WormholeFrameDetector;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class WormholePortalBlock extends BaseEntityBlock {
    public static final MapCodec<WormholePortalBlock> CODEC = simpleCodec(WormholePortalBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;

    // Thin shapes like nether portal (4 pixels thick)
    protected static final VoxelShape X_AXIS_SHAPE = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    protected static final VoxelShape Z_AXIS_SHAPE = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    // Cooldown key for entity persistent data
    public static final String WORMHOLE_COOLDOWN_KEY = "trekcraft_wormhole_cooldown";
    public static final int COOLDOWN_TICKS = 100; // 5 seconds

    public WormholePortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS_SHAPE : X_AXIS_SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WormholePortalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Get portal ID from block entity
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof WormholePortalBlockEntity portalBE)) {
            com.csquared.trekcraft.TrekCraftMod.LOGGER.warn("entityInside: BlockEntity at {} is not WormholePortalBlockEntity", pos);
            return;
        }

        UUID portalId = portalBE.getPortalId();
        if (portalId == null) {
            com.csquared.trekcraft.TrekCraftMod.LOGGER.warn("entityInside: Portal at {} has null portalId", pos);
            return;
        }

        // Teleport through the wormhole
        WormholeService.teleportThrough(serverLevel, entity, portalId);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                   LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        // Check if frame is still valid when a neighbor changes
        if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WormholePortalBlockEntity portalBE) {
                UUID portalId = portalBE.getPortalId();
                if (portalId != null) {
                    TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
                    WormholeRecord wormhole = data.getWormhole(portalId).orElse(null);
                    if (wormhole != null) {
                        // Schedule a check for next tick to avoid modification during update
                        serverLevel.scheduleTick(pos, this, 1);
                    }
                }
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        // Validate the portal frame
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof WormholePortalBlockEntity portalBE) {
            UUID portalId = portalBE.getPortalId();
            if (portalId != null) {
                TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);
                WormholeRecord wormhole = data.getWormhole(portalId).orElse(null);
                if (wormhole != null) {
                    boolean frameValid = WormholeFrameDetector.isFrameStillValid(
                            level, wormhole.anchorPos(), wormhole.axis(), wormhole.width(), wormhole.height());
                    if (!frameValid) {
                        WormholeService.destroyPortal(level, portalId);
                    }
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof WormholePortalBlockEntity portalBE) {
                    UUID portalId = portalBE.getPortalId();
                    if (portalId != null) {
                        // Mark the portal as needing destruction but don't cascade here
                        // The frame destruction will be handled by neighborChanged
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

}
