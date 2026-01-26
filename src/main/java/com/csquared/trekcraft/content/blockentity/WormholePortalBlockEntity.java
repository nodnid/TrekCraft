package com.csquared.trekcraft.content.blockentity;

import com.csquared.trekcraft.registry.ModBlockEntities;
import com.csquared.trekcraft.service.WormholeService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Block entity for wormhole portal blocks.
 * Stores the portal ID that links to the WormholeRecord in SavedData.
 */
public class WormholePortalBlockEntity extends BlockEntity {
    private UUID portalId;

    public WormholePortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WORMHOLE_PORTAL.get(), pos, state);
    }

    public UUID getPortalId() {
        return portalId;
    }

    public void setPortalId(UUID portalId) {
        this.portalId = portalId;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (portalId != null) {
            tag.putUUID("PortalId", portalId);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("PortalId")) {
            portalId = tag.getUUID("PortalId");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (portalId != null) {
            tag.putUUID("PortalId", portalId);
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Server-side ticker that checks for entities inside the portal.
     * More reliable than entityInside on lagging servers.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, WormholePortalBlockEntity blockEntity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        UUID portalId = blockEntity.getPortalId();
        if (portalId == null) {
            return;
        }

        // Check for entities in this block position
        // Use a slightly expanded AABB for more reliable detection
        AABB detectionBox = new AABB(pos).inflate(0.1);
        List<Entity> entities = level.getEntities(null, detectionBox);

        for (Entity entity : entities) {
            WormholeService.teleportThrough(serverLevel, entity, portalId);
        }
    }
}
