package com.csquared.trekcraft.service;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.block.WormholePortalBlock;
import com.csquared.trekcraft.content.blockentity.WormholePortalBlockEntity;
import com.csquared.trekcraft.data.TransporterNetworkSavedData;
import com.csquared.trekcraft.data.WormholeRecord;
import com.csquared.trekcraft.registry.ModBlocks;
import com.csquared.trekcraft.util.WormholeFrameDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Service class for wormhole portal operations.
 */
public class WormholeService {

    /**
     * Result of a portal activation attempt.
     */
    public enum ActivationResult {
        SUCCESS,
        NOT_COBBLESTONE,
        INVALID_FRAME,
        PORTAL_EXISTS_HERE
    }

    /**
     * Try to activate a wormhole portal at the clicked position.
     *
     * @param player The player activating the portal
     * @param clickedPos The position that was clicked (should be cobblestone)
     * @return The result of the activation attempt, and if successful, the portal ID
     */
    public static ActivationAttempt tryActivate(Player player, BlockPos clickedPos) {
        if (player.level().isClientSide || !(player.level() instanceof ServerLevel serverLevel)) {
            return new ActivationAttempt(ActivationResult.INVALID_FRAME, null);
        }

        // Detect the frame
        WormholeFrameDetector.FrameResult frameResult = WormholeFrameDetector.detectFrame(serverLevel, clickedPos);

        if (!frameResult.isValid()) {
            TrekCraftMod.LOGGER.debug("Frame detection failed: {}", frameResult.errorMessage());
            if (frameResult.errorMessage().equals("Must click on cobblestone")) {
                return new ActivationAttempt(ActivationResult.NOT_COBBLESTONE, null);
            }
            return new ActivationAttempt(ActivationResult.INVALID_FRAME, null);
        }

        // Check if a portal already exists at this anchor position
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(serverLevel);
        if (data.getWormholeByAnchor(frameResult.anchorPos()).isPresent()) {
            return new ActivationAttempt(ActivationResult.PORTAL_EXISTS_HERE, null);
        }

        // Create the portal
        UUID portalId = UUID.randomUUID();
        String dimensionKey = serverLevel.dimension().location().toString();

        // Create and register the wormhole record
        WormholeRecord wormhole = WormholeRecord.create(
                portalId,
                "Wormhole-" + portalId.toString().substring(0, 4).toUpperCase(),
                frameResult.anchorPos(),
                frameResult.axis(),
                frameResult.width(),
                frameResult.height(),
                dimensionKey
        );
        data.registerWormhole(wormhole);

        // Place portal blocks in the interior
        BlockState portalState = ModBlocks.WORMHOLE_PORTAL.get().defaultBlockState()
                .setValue(WormholePortalBlock.AXIS, frameResult.axis());

        for (BlockPos interiorPos : frameResult.interiorBlocks()) {
            serverLevel.setBlock(interiorPos, portalState, 3);
            BlockEntity be = serverLevel.getBlockEntity(interiorPos);
            if (be instanceof WormholePortalBlockEntity portalBE) {
                portalBE.setPortalId(portalId);
            }
        }

        TrekCraftMod.LOGGER.info("Wormhole portal created at {} with ID {}", frameResult.anchorPos(), portalId);
        return new ActivationAttempt(ActivationResult.SUCCESS, portalId);
    }

    /**
     * Result of an activation attempt.
     */
    public record ActivationAttempt(ActivationResult result, UUID portalId) {
        public boolean isSuccess() {
            return result == ActivationResult.SUCCESS;
        }
    }

    /**
     * Teleport an entity through a wormhole portal.
     */
    public static void teleportThrough(ServerLevel level, Entity entity, UUID portalId) {
        // Check cooldown
        CompoundTag persistentData = entity.getPersistentData();
        long lastTeleportTime = persistentData.getLong(WormholePortalBlock.WORMHOLE_COOLDOWN_KEY);
        long currentTime = level.getGameTime();

        if (currentTime - lastTeleportTime < WormholePortalBlock.COOLDOWN_TICKS) {
            return; // Still on cooldown
        }

        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);
        WormholeRecord sourceWormhole = data.getWormhole(portalId).orElse(null);

        if (sourceWormhole == null || !sourceWormhole.isLinked()) {
            return; // Portal doesn't exist or isn't linked
        }

        WormholeRecord destWormhole = data.getWormhole(sourceWormhole.linkedPortalId()).orElse(null);
        if (destWormhole == null) {
            return; // Destination portal doesn't exist
        }

        // Calculate destination position (center of the portal interior)
        BlockPos destAnchor = destWormhole.anchorPos();
        Direction.Axis axis = destWormhole.axis();
        int width = destWormhole.width();
        int height = destWormhole.height();

        // Find center of portal
        Direction horizontal = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        double centerX = destAnchor.getX() + (axis == Direction.Axis.X ? width / 2.0 : 0.5);
        double centerY = destAnchor.getY() + height / 2.0;
        double centerZ = destAnchor.getZ() + (axis == Direction.Axis.Z ? width / 2.0 : 0.5);

        // Offset slightly from the portal surface to avoid immediate re-entry
        double offset = 0.5;
        if (axis == Direction.Axis.X) {
            // Portal faces north/south, offset in Z
            centerZ += offset;
        } else {
            // Portal faces east/west, offset in X
            centerX += offset;
        }

        // Set cooldown before teleporting
        persistentData.putLong(WormholePortalBlock.WORMHOLE_COOLDOWN_KEY, currentTime);

        // Teleport the entity
        entity.teleportTo(centerX, centerY, centerZ);
        entity.setDeltaMovement(Vec3.ZERO);

        // Play effects
        level.playSound(null, entity.blockPosition(),
                net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);

        TrekCraftMod.LOGGER.debug("Entity {} teleported through wormhole {} to {}", entity.getName().getString(), portalId, destAnchor);
    }

    /**
     * Link two portals together bidirectionally.
     */
    public static boolean linkPortals(ServerLevel level, UUID portal1Id, UUID portal2Id) {
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        WormholeRecord portal1 = data.getWormhole(portal1Id).orElse(null);
        WormholeRecord portal2 = data.getWormhole(portal2Id).orElse(null);

        if (portal1 == null || portal2 == null) {
            return false;
        }

        // Can't link if either is already linked
        if (portal1.isLinked() || portal2.isLinked()) {
            return false;
        }

        // Must be in same dimension
        if (!portal1.dimensionKey().equals(portal2.dimensionKey())) {
            return false;
        }

        // Update both portals with links
        data.updateWormhole(portal1.withLink(portal2Id));
        data.updateWormhole(portal2.withLink(portal1Id));

        TrekCraftMod.LOGGER.info("Linked wormholes {} ({}) and {} ({})",
                portal1.name(), portal1Id, portal2.name(), portal2Id);
        return true;
    }

    /**
     * Unlink a portal (removes link from both sides).
     */
    public static void unlinkPortal(ServerLevel level, UUID portalId) {
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        WormholeRecord portal = data.getWormhole(portalId).orElse(null);
        if (portal == null || !portal.isLinked()) {
            return;
        }

        UUID linkedId = portal.linkedPortalId();
        WormholeRecord linkedPortal = data.getWormhole(linkedId).orElse(null);

        // Unlink both sides
        data.updateWormhole(portal.withoutLink());
        if (linkedPortal != null) {
            data.updateWormhole(linkedPortal.withoutLink());
        }

        TrekCraftMod.LOGGER.info("Unlinked wormhole {}", portalId);
    }

    /**
     * Destroy a portal, removing all its blocks and unregistering it.
     */
    public static void destroyPortal(ServerLevel level, UUID portalId) {
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);

        WormholeRecord wormhole = data.getWormhole(portalId).orElse(null);
        if (wormhole == null) {
            return;
        }

        // Unlink first (this affects the other portal but keeps it intact)
        if (wormhole.isLinked()) {
            unlinkPortal(level, portalId);
        }

        // Remove all portal blocks
        Direction horizontal = wormhole.axis() == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        for (int y = 0; y < wormhole.height(); y++) {
            for (int x = 0; x < wormhole.width(); x++) {
                BlockPos pos = wormhole.anchorPos().above(y).relative(horizontal, x);
                if (level.getBlockState(pos).is(ModBlocks.WORMHOLE_PORTAL.get())) {
                    level.removeBlock(pos, false);
                }
            }
        }

        // Unregister from saved data
        data.unregisterWormhole(portalId);

        TrekCraftMod.LOGGER.info("Destroyed wormhole portal {} at {}", portalId, wormhole.anchorPos());
    }

    /**
     * Rename a wormhole portal.
     */
    public static void renameWormhole(ServerLevel level, UUID portalId, String newName) {
        TransporterNetworkSavedData data = TransporterNetworkSavedData.get(level);
        WormholeRecord wormhole = data.getWormhole(portalId).orElse(null);
        if (wormhole != null) {
            data.updateWormhole(wormhole.withName(newName));
            TrekCraftMod.LOGGER.debug("Renamed wormhole {} to {}", portalId, newName);
        }
    }
}
