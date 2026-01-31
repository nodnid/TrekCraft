package com.csquared.trekcraft.content.block;

import com.csquared.trekcraft.content.blockentity.MobileEmitterBlockEntity;
import com.csquared.trekcraft.registry.ModBlockEntities;
import com.csquared.trekcraft.registry.ModBlocks;
import com.csquared.trekcraft.registry.ModItems;
import com.csquared.trekcraft.service.MobileEmitterService;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Mobile emitter block that forms corners of an open-air holo-area.
 * Four emitters linked via tricorder create a rectangular area extending to world height.
 */
public class MobileEmitterBlock extends BaseEntityBlock {
    public static final MapCodec<MobileEmitterBlock> CODEC = simpleCodec(MobileEmitterBlock::new);

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public MobileEmitterBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobileEmitterBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, ModBlockEntities.MOBILE_EMITTER.get(),
            MobileEmitterBlockEntity::serverTick);
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
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hitResult) {
        // Only handle tricorder interactions
        if (!stack.is(ModItems.TRICORDER.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MobileEmitterBlockEntity emitter)) {
            return ItemInteractionResult.FAIL;
        }

        ServerPlayer serverPlayer = (ServerPlayer) player;

        // Check if this emitter is part of a network
        UUID networkId = emitter.getNetworkId();

        if (networkId == null) {
            // Not in a network - check for nearby networks to join or create new one
            List<NearbyNetwork> nearbyNetworks = findNearbyNetworks(level, pos, 32);

            if (nearbyNetworks.isEmpty()) {
                // Create new network with this emitter as primary
                UUID newNetworkId = emitter.createNetwork();
                player.displayClientMessage(
                    Component.literal("Created new mobile emitter network. Link 3 more emitters to complete."),
                    true
                );
            } else if (nearbyNetworks.size() == 1) {
                // Only one nearby network - auto-link
                NearbyNetwork network = nearbyNetworks.get(0);
                if (network.emitterCount >= 4) {
                    player.displayClientMessage(
                        Component.literal("Nearby network already has 4 emitters."),
                        true
                    );
                } else {
                    if (emitter.linkToNetwork(network.networkId, network.primaryPos)) {
                        player.displayClientMessage(
                            Component.literal("Linked to network. " + (network.emitterCount + 1) + "/4 emitters."),
                            true
                        );
                    } else {
                        player.displayClientMessage(
                            Component.literal("Failed to link to network."),
                            true
                        );
                    }
                }
            } else {
                // Multiple nearby networks - inform player
                player.displayClientMessage(
                    Component.literal("Multiple networks nearby. Break and replace emitter near desired network."),
                    true
                );
            }
        } else {
            // Already in a network - show status or activate
            if (emitter.isPrimary()) {
                handlePrimaryInteraction(emitter, serverPlayer);
            } else {
                // Secondary emitter - find primary and show status
                player.displayClientMessage(
                    Component.literal("Emitter linked to network. Use tricorder on primary emitter to manage."),
                    true
                );
            }
        }

        return ItemInteractionResult.SUCCESS;
    }

    /**
     * Handle tricorder interaction on the primary emitter.
     */
    private void handlePrimaryInteraction(MobileEmitterBlockEntity emitter, ServerPlayer player) {
        int count = emitter.getNetworkSize();

        if (emitter.isActive()) {
            // Network is active - deactivate it
            emitter.deactivate();
            player.displayClientMessage(
                Component.literal("Mobile emitter network deactivated."),
                true
            );
        } else if (count < 4) {
            // Not enough emitters
            player.displayClientMessage(
                Component.literal("Network incomplete: " + count + "/4 emitters linked."),
                true
            );
        } else {
            // Try to activate
            MobileEmitterService.ValidationResult result = emitter.tryActivate();
            if (result == MobileEmitterService.ValidationResult.VALID) {
                player.displayClientMessage(
                    Component.literal("Mobile emitter network activated! Holo-area ready."),
                    true
                );
            } else {
                player.displayClientMessage(
                    Component.literal("Cannot activate: " + MobileEmitterService.getErrorMessage(result)),
                    true
                );
            }
        }
    }

    /**
     * Find nearby mobile emitter networks.
     */
    private List<NearbyNetwork> findNearbyNetworks(Level level, BlockPos origin, int radius) {
        Map<UUID, NearbyNetwork> networks = new HashMap<>();

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = origin.offset(x, y, z);
                    if (checkPos.equals(origin)) continue;

                    BlockEntity be = level.getBlockEntity(checkPos);
                    if (be instanceof MobileEmitterBlockEntity emitter) {
                        UUID netId = emitter.getNetworkId();
                        if (netId != null && !networks.containsKey(netId)) {
                            // Find the primary for this network
                            if (emitter.isPrimary()) {
                                networks.put(netId, new NearbyNetwork(
                                    netId,
                                    checkPos,
                                    emitter.getNetworkSize()
                                ));
                            }
                        }
                    }
                }
            }
        }

        // For networks where we didn't find primary, search again
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos checkPos = origin.offset(x, y, z);
                    BlockEntity be = level.getBlockEntity(checkPos);
                    if (be instanceof MobileEmitterBlockEntity emitter) {
                        UUID netId = emitter.getNetworkId();
                        if (netId != null && emitter.isPrimary() && !networks.containsKey(netId)) {
                            networks.put(netId, new NearbyNetwork(
                                netId,
                                checkPos,
                                emitter.getNetworkSize()
                            ));
                        }
                    }
                }
            }
        }

        return new ArrayList<>(networks.values());
    }

    private record NearbyNetwork(UUID networkId, BlockPos primaryPos, int emitterCount) {}

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
                           ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);

        if (!level.isClientSide && placer instanceof Player player) {
            player.displayClientMessage(
                Component.literal("Mobile emitter placed. Use tricorder to create or join a network."),
                true
            );
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MobileEmitterBlockEntity emitter) {
                emitter.onRemoved();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
