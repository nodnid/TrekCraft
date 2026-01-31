package com.csquared.trekcraft.content.blockentity;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.block.HolodeckControllerBlock;
import com.csquared.trekcraft.content.block.HolodeckEmitterBlock;
import com.csquared.trekcraft.holodeck.HoloprogramManager;
import com.csquared.trekcraft.registry.ModBlockEntities;
import com.csquared.trekcraft.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Block entity for the Holodeck Controller.
 * Manages frame validation, player tracking, game mode switching, and holoprogram operations.
 */
public class HolodeckControllerBlockEntity extends BlockEntity {

    // Frame structure data
    private Set<BlockPos> framePositions = new HashSet<>();
    private BlockPos interiorMin = null;
    private BlockPos interiorMax = null;
    private boolean active = false;

    // Player tracking
    private final Map<UUID, GameType> originalGameModes = new HashMap<>();
    private final Map<UUID, ListTag> originalInventories = new HashMap<>();
    private final Set<UUID> playersInside = new HashSet<>();

    public HolodeckControllerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLODECK_CONTROLLER.get(), pos, state);
    }

    /**
     * Server tick - track players and manage game modes.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HolodeckControllerBlockEntity be) {
        if (level.isClientSide || !be.active) return;

        ServerLevel serverLevel = (ServerLevel) level;
        be.tickPlayerTracking(serverLevel);
    }

    /**
     * Track players entering and exiting the holodeck.
     */
    private void tickPlayerTracking(ServerLevel level) {
        if (interiorMin == null || interiorMax == null) return;

        AABB bounds = new AABB(
            interiorMin.getX(), interiorMin.getY(), interiorMin.getZ(),
            interiorMax.getX() + 1, interiorMax.getY() + 1, interiorMax.getZ() + 1
        );
        Set<UUID> currentPlayersInside = new HashSet<>();

        // Check all players on the server
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            // Only check players in the same dimension
            if (!player.level().dimension().equals(level.dimension())) continue;

            if (bounds.contains(player.getX(), player.getY(), player.getZ())) {
                currentPlayersInside.add(player.getUUID());

                // Player just entered
                if (!playersInside.contains(player.getUUID())) {
                    onPlayerEnter(player);
                }
            }
        }

        // Check for players who left
        Set<UUID> leftPlayers = new HashSet<>(playersInside);
        leftPlayers.removeAll(currentPlayersInside);

        for (UUID playerId : leftPlayers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                onPlayerExit(player);
            } else {
                // Player disconnected while inside - clean up stored data
                originalGameModes.remove(playerId);
                originalInventories.remove(playerId);
            }
        }

        // Update current players set
        boolean wasEmpty = playersInside.isEmpty();
        playersInside.clear();
        playersInside.addAll(currentPlayersInside);

        // Clear blocks when all players exit
        if (!wasEmpty && playersInside.isEmpty()) {
            clearInterior(level);
        }

        setChanged();
    }

    /**
     * Handle player entering the holodeck.
     */
    private void onPlayerEnter(ServerPlayer player) {
        // Store original game mode
        originalGameModes.put(player.getUUID(), player.gameMode.getGameModeForPlayer());

        // Store original inventory
        ListTag inventoryTag = new ListTag();
        player.getInventory().save(inventoryTag);
        originalInventories.put(player.getUUID(), inventoryTag);

        // Switch to Creative mode
        player.setGameMode(GameType.CREATIVE);

        TrekCraftMod.LOGGER.debug("Player {} entered holodeck at {}", player.getName().getString(), worldPosition);
        setChanged();
    }

    /**
     * Handle player exiting the holodeck.
     */
    private void onPlayerExit(ServerPlayer player) {
        UUID playerId = player.getUUID();

        // Restore original game mode
        GameType originalMode = originalGameModes.remove(playerId);
        if (originalMode != null) {
            player.setGameMode(originalMode);
        }

        // Restore original inventory
        ListTag savedInventory = originalInventories.remove(playerId);
        if (savedInventory != null) {
            player.getInventory().load(savedInventory);
        }

        playersInside.remove(playerId);

        TrekCraftMod.LOGGER.debug("Player {} exited holodeck at {}", player.getName().getString(), worldPosition);
        setChanged();
    }

    /**
     * Clear all blocks inside the holodeck interior (except structure blocks).
     */
    private void clearInterior(ServerLevel level) {
        if (interiorMin == null || interiorMax == null) return;

        for (int x = interiorMin.getX(); x <= interiorMax.getX(); x++) {
            for (int y = interiorMin.getY(); y <= interiorMax.getY(); y++) {
                for (int z = interiorMin.getZ(); z <= interiorMax.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    // Skip holodeck structure blocks
                    if (state.is(ModBlocks.HOLODECK_EMITTER.get()) ||
                        state.is(ModBlocks.HOLODECK_CONTROLLER.get())) {
                        continue;
                    }

                    // Replace with air
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        TrekCraftMod.LOGGER.debug("Cleared holodeck interior at {}", worldPosition);
    }

    /**
     * Validate the holodeck frame structure and activate if valid.
     * The controller must be adjacent to emitters that form a complete rectangular frame.
     */
    public boolean validateAndActivateFrame() {
        if (level == null || level.isClientSide) return false;

        // Find adjacent emitter to start flood fill
        BlockPos startEmitter = findAdjacentEmitter();
        if (startEmitter == null) {
            deactivate();
            return false;
        }

        // Flood fill to find all connected emitters
        Set<BlockPos> emitters = floodFillEmitters(startEmitter);
        if (emitters.isEmpty()) {
            deactivate();
            return false;
        }

        // Verify emitters form a valid rectangular frame
        if (!validateRectangularFrame(emitters)) {
            deactivate();
            return false;
        }

        // Calculate interior bounds
        calculateInteriorBounds(emitters);

        // Activate the holodeck
        activate(emitters);
        return true;
    }

    /**
     * Find an emitter block adjacent to this controller.
     */
    @Nullable
    private BlockPos findAdjacentEmitter() {
        BlockPos[] neighbors = {
            worldPosition.north(),
            worldPosition.south(),
            worldPosition.east(),
            worldPosition.west(),
            worldPosition.above(),
            worldPosition.below()
        };

        for (BlockPos neighbor : neighbors) {
            if (level.getBlockState(neighbor).is(ModBlocks.HOLODECK_EMITTER.get())) {
                return neighbor;
            }
        }
        return null;
    }

    /**
     * Flood fill to find all connected emitter blocks.
     */
    private Set<BlockPos> floodFillEmitters(BlockPos start) {
        Set<BlockPos> emitters = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (emitters.contains(current)) continue;

            BlockState state = level.getBlockState(current);
            if (!state.is(ModBlocks.HOLODECK_EMITTER.get())) continue;

            emitters.add(current);

            // Check all 6 neighbors
            queue.add(current.north());
            queue.add(current.south());
            queue.add(current.east());
            queue.add(current.west());
            queue.add(current.above());
            queue.add(current.below());
        }

        return emitters;
    }

    /**
     * Validate that emitters form a complete rectangular frame (all 12 edges).
     */
    private boolean validateRectangularFrame(Set<BlockPos> emitters) {
        if (emitters.size() < 12) return false; // Minimum for 1x1x1 interior

        // Find bounding box
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : emitters) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // Check that all 12 edges are complete
        // A rectangular frame has 12 edges (4 along each axis)

        // Bottom face edges (y = minY)
        for (int x = minX; x <= maxX; x++) {
            if (!emitters.contains(new BlockPos(x, minY, minZ))) return false;
            if (!emitters.contains(new BlockPos(x, minY, maxZ))) return false;
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (!emitters.contains(new BlockPos(minX, minY, z))) return false;
            if (!emitters.contains(new BlockPos(maxX, minY, z))) return false;
        }

        // Top face edges (y = maxY)
        for (int x = minX; x <= maxX; x++) {
            if (!emitters.contains(new BlockPos(x, maxY, minZ))) return false;
            if (!emitters.contains(new BlockPos(x, maxY, maxZ))) return false;
        }
        for (int z = minZ; z <= maxZ; z++) {
            if (!emitters.contains(new BlockPos(minX, maxY, z))) return false;
            if (!emitters.contains(new BlockPos(maxX, maxY, z))) return false;
        }

        // Vertical edges
        for (int y = minY; y <= maxY; y++) {
            if (!emitters.contains(new BlockPos(minX, y, minZ))) return false;
            if (!emitters.contains(new BlockPos(maxX, y, minZ))) return false;
            if (!emitters.contains(new BlockPos(minX, y, maxZ))) return false;
            if (!emitters.contains(new BlockPos(maxX, y, maxZ))) return false;
        }

        // Verify there are no extra emitters inside (only edges)
        for (BlockPos pos : emitters) {
            boolean onEdge =
                (pos.getX() == minX || pos.getX() == maxX) ||
                (pos.getY() == minY || pos.getY() == maxY) ||
                (pos.getZ() == minZ || pos.getZ() == maxZ);

            // Actually, emitters should only be on edges
            // An edge position has exactly 2 coordinates at min/max values
            int edgeCount = 0;
            if (pos.getX() == minX || pos.getX() == maxX) edgeCount++;
            if (pos.getY() == minY || pos.getY() == maxY) edgeCount++;
            if (pos.getZ() == minZ || pos.getZ() == maxZ) edgeCount++;

            if (edgeCount < 2) return false; // Not on an edge
        }

        return true;
    }

    /**
     * Calculate the interior bounds of the holodeck (1 block inward from frame).
     */
    private void calculateInteriorBounds(Set<BlockPos> emitters) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : emitters) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minY = Math.min(minY, pos.getY());
            maxY = Math.max(maxY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        // Interior is 1 block inward from the frame
        interiorMin = new BlockPos(minX + 1, minY + 1, minZ + 1);
        interiorMax = new BlockPos(maxX - 1, maxY - 1, maxZ - 1);
    }

    /**
     * Activate the holodeck with the validated frame.
     */
    private void activate(Set<BlockPos> emitters) {
        this.framePositions = emitters;
        this.active = true;

        // Update controller block state
        level.setBlock(worldPosition, getBlockState().setValue(HolodeckControllerBlock.ACTIVE, true), 3);

        // Update all emitters to active state
        for (BlockPos pos : emitters) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.HOLODECK_EMITTER.get())) {
                level.setBlock(pos, state.setValue(HolodeckEmitterBlock.ACTIVE, true), 3);
            }
        }

        setChanged();
    }

    /**
     * Deactivate the holodeck.
     */
    public void deactivate() {
        // Restore all players inside
        if (level instanceof ServerLevel serverLevel) {
            for (UUID playerId : new HashSet<>(playersInside)) {
                ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
                if (player != null) {
                    onPlayerExit(player);
                }
            }
        }

        // Update controller block state (only if the block still exists)
        if (level != null && !level.isClientSide) {
            BlockState currentState = level.getBlockState(worldPosition);
            if (currentState.is(ModBlocks.HOLODECK_CONTROLLER.get())) {
                level.setBlock(worldPosition, currentState.setValue(HolodeckControllerBlock.ACTIVE, false), 3);
            }
        }

        // Deactivate all emitters
        for (BlockPos pos : framePositions) {
            if (level != null) {
                BlockState state = level.getBlockState(pos);
                if (state.is(ModBlocks.HOLODECK_EMITTER.get())) {
                    level.setBlock(pos, state.setValue(HolodeckEmitterBlock.ACTIVE, false), 3);
                }
            }
        }

        framePositions.clear();
        interiorMin = null;
        interiorMax = null;
        active = false;
        playersInside.clear();
        originalGameModes.clear();
        originalInventories.clear();

        setChanged();
    }

    /**
     * Save a holoprogram from the current interior.
     */
    public boolean saveHoloprogram(String name) {
        if (!active || level == null || level.isClientSide) return false;
        if (interiorMin == null || interiorMax == null) return false;

        return HoloprogramManager.save((ServerLevel) level, name, interiorMin, interiorMax);
    }

    /**
     * Load a holoprogram into the interior.
     */
    public boolean loadHoloprogram(String name) {
        if (!active || level == null || level.isClientSide) return false;
        if (interiorMin == null || interiorMax == null) return false;

        // First clear the interior
        clearInterior((ServerLevel) level);

        // Then load the holoprogram
        return HoloprogramManager.load((ServerLevel) level, name, interiorMin);
    }

    /**
     * Get list of available holoprograms.
     */
    public List<String> getHoloprogramList() {
        return HoloprogramManager.listHoloprograms();
    }

    /**
     * Delete a holoprogram.
     */
    public boolean deleteHoloprogram(String name) {
        return HoloprogramManager.delete(name);
    }

    /**
     * Manually clear the holodeck interior.
     */
    public void manualClear() {
        if (!active || level == null || level.isClientSide) return;
        if (interiorMin == null || interiorMax == null) return;
        clearInterior((ServerLevel) level);
    }

    public boolean isActive() {
        return active;
    }

    public BlockPos getInteriorMin() {
        return interiorMin;
    }

    public BlockPos getInteriorMax() {
        return interiorMax;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putBoolean("Active", active);

        if (interiorMin != null) {
            tag.putInt("InteriorMinX", interiorMin.getX());
            tag.putInt("InteriorMinY", interiorMin.getY());
            tag.putInt("InteriorMinZ", interiorMin.getZ());
        }

        if (interiorMax != null) {
            tag.putInt("InteriorMaxX", interiorMax.getX());
            tag.putInt("InteriorMaxY", interiorMax.getY());
            tag.putInt("InteriorMaxZ", interiorMax.getZ());
        }

        // Save frame positions
        ListTag frameList = new ListTag();
        for (BlockPos pos : framePositions) {
            CompoundTag posTag = new CompoundTag();
            posTag.putInt("X", pos.getX());
            posTag.putInt("Y", pos.getY());
            posTag.putInt("Z", pos.getZ());
            frameList.add(posTag);
        }
        tag.put("FramePositions", frameList);

        // Save player game modes
        ListTag gameModesTag = new ListTag();
        for (Map.Entry<UUID, GameType> entry : originalGameModes.entrySet()) {
            CompoundTag modeTag = new CompoundTag();
            modeTag.putUUID("Player", entry.getKey());
            modeTag.putInt("GameMode", entry.getValue().getId());
            gameModesTag.add(modeTag);
        }
        tag.put("OriginalGameModes", gameModesTag);

        // Save player inventories
        ListTag inventoriesTag = new ListTag();
        for (Map.Entry<UUID, ListTag> entry : originalInventories.entrySet()) {
            CompoundTag invTag = new CompoundTag();
            invTag.putUUID("Player", entry.getKey());
            invTag.put("Inventory", entry.getValue());
            inventoriesTag.add(invTag);
        }
        tag.put("OriginalInventories", inventoriesTag);

        // Save players inside set
        ListTag playersTag = new ListTag();
        for (UUID playerId : playersInside) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", playerId);
            playersTag.add(playerTag);
        }
        tag.put("PlayersInside", playersTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        active = tag.getBoolean("Active");

        if (tag.contains("InteriorMinX")) {
            interiorMin = new BlockPos(
                tag.getInt("InteriorMinX"),
                tag.getInt("InteriorMinY"),
                tag.getInt("InteriorMinZ")
            );
        }

        if (tag.contains("InteriorMaxX")) {
            interiorMax = new BlockPos(
                tag.getInt("InteriorMaxX"),
                tag.getInt("InteriorMaxY"),
                tag.getInt("InteriorMaxZ")
            );
        }

        // Load frame positions
        framePositions.clear();
        if (tag.contains("FramePositions")) {
            ListTag frameList = tag.getList("FramePositions", Tag.TAG_COMPOUND);
            for (int i = 0; i < frameList.size(); i++) {
                CompoundTag posTag = frameList.getCompound(i);
                framePositions.add(new BlockPos(
                    posTag.getInt("X"),
                    posTag.getInt("Y"),
                    posTag.getInt("Z")
                ));
            }
        }

        // Load player game modes
        originalGameModes.clear();
        if (tag.contains("OriginalGameModes")) {
            ListTag gameModesTag = tag.getList("OriginalGameModes", Tag.TAG_COMPOUND);
            for (int i = 0; i < gameModesTag.size(); i++) {
                CompoundTag modeTag = gameModesTag.getCompound(i);
                UUID playerId = modeTag.getUUID("Player");
                GameType mode = GameType.byId(modeTag.getInt("GameMode"));
                originalGameModes.put(playerId, mode);
            }
        }

        // Load player inventories
        originalInventories.clear();
        if (tag.contains("OriginalInventories")) {
            ListTag inventoriesTag = tag.getList("OriginalInventories", Tag.TAG_COMPOUND);
            for (int i = 0; i < inventoriesTag.size(); i++) {
                CompoundTag invTag = inventoriesTag.getCompound(i);
                UUID playerId = invTag.getUUID("Player");
                ListTag inventory = invTag.getList("Inventory", Tag.TAG_COMPOUND);
                originalInventories.put(playerId, inventory);
            }
        }

        // Load players inside set
        playersInside.clear();
        if (tag.contains("PlayersInside")) {
            ListTag playersTag = tag.getList("PlayersInside", Tag.TAG_COMPOUND);
            for (int i = 0; i < playersTag.size(); i++) {
                CompoundTag playerTag = playersTag.getCompound(i);
                playersInside.add(playerTag.getUUID("Player"));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Active", active);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
