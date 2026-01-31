package com.csquared.trekcraft.content.blockentity;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.content.block.MobileEmitterBlock;
import com.csquared.trekcraft.holodeck.HoloprogramManager;
import com.csquared.trekcraft.registry.ModBlockEntities;
import com.csquared.trekcraft.registry.ModBlocks;
import com.csquared.trekcraft.service.MobileEmitterService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
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
 * Block entity for Mobile Emitters.
 * Primary emitter stores network state and handles player tracking.
 * Secondary emitters just store their network ID reference.
 */
public class MobileEmitterBlockEntity extends BlockEntity {

    // Network identity
    private UUID networkId = null;
    private boolean isPrimary = false;
    private List<BlockPos> linkedPositions = new ArrayList<>();

    // Interior bounds (only used by primary)
    private BlockPos interiorMin = null;
    private BlockPos interiorMax = null;
    private boolean active = false;

    // Player tracking (only used by primary)
    private final Map<UUID, GameType> originalGameModes = new HashMap<>();
    private final Map<UUID, ListTag> originalInventories = new HashMap<>();
    private final Set<UUID> playersInside = new HashSet<>();

    public MobileEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOBILE_EMITTER.get(), pos, state);
    }

    /**
     * Server tick - track players and manage game modes (primary only).
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, MobileEmitterBlockEntity be) {
        if (level.isClientSide || !be.active || !be.isPrimary) return;

        ServerLevel serverLevel = (ServerLevel) level;
        be.tickPlayerTracking(serverLevel);
    }

    /**
     * Track players entering and exiting the holo-area.
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
     * Handle player entering the holo-area.
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

        TrekCraftMod.LOGGER.debug("Player {} entered mobile emitter area at {}", player.getName().getString(), worldPosition);
        setChanged();
    }

    /**
     * Handle player exiting the holo-area.
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

        TrekCraftMod.LOGGER.debug("Player {} exited mobile emitter area at {}", player.getName().getString(), worldPosition);
        setChanged();
    }

    /**
     * Clear all blocks inside the holo-area (except mobile emitters).
     */
    private void clearInterior(ServerLevel level) {
        if (interiorMin == null || interiorMax == null) return;

        for (int x = interiorMin.getX(); x <= interiorMax.getX(); x++) {
            for (int y = interiorMin.getY(); y <= interiorMax.getY(); y++) {
                for (int z = interiorMin.getZ(); z <= interiorMax.getZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    // Skip mobile emitter blocks
                    if (state.is(ModBlocks.MOBILE_EMITTER.get())) {
                        continue;
                    }

                    // Replace with air
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        TrekCraftMod.LOGGER.debug("Cleared mobile emitter area at {}", worldPosition);
    }

    /**
     * Create a new network with this emitter as primary.
     */
    public UUID createNetwork() {
        this.networkId = UUID.randomUUID();
        this.isPrimary = true;
        this.linkedPositions = new ArrayList<>();
        this.linkedPositions.add(worldPosition);
        setChanged();
        return networkId;
    }

    /**
     * Link this emitter to an existing network.
     */
    public boolean linkToNetwork(UUID networkId, BlockPos primaryPos) {
        if (level == null || level.isClientSide) return false;

        // Find the primary emitter
        BlockEntity be = level.getBlockEntity(primaryPos);
        if (!(be instanceof MobileEmitterBlockEntity primary) || !primary.isPrimary()) {
            return false;
        }

        // Check network ID matches
        if (!networkId.equals(primary.getNetworkId())) {
            return false;
        }

        // Check if network already has 4 emitters
        if (primary.getLinkedPositions().size() >= 4) {
            return false;
        }

        // Link this emitter
        this.networkId = networkId;
        this.isPrimary = false;

        // Add to primary's linked positions
        primary.addLinkedPosition(worldPosition);

        setChanged();
        return true;
    }

    /**
     * Add a position to the linked positions list (primary only).
     */
    public void addLinkedPosition(BlockPos pos) {
        if (!isPrimary) return;
        if (!linkedPositions.contains(pos)) {
            linkedPositions.add(pos);
            setChanged();
        }
    }

    /**
     * Remove a position from the linked positions list (primary only).
     */
    public void removeLinkedPosition(BlockPos pos) {
        if (!isPrimary) return;
        linkedPositions.remove(pos);
        setChanged();
    }

    /**
     * Attempt to activate the network. Only callable on primary emitter.
     * @return ValidationResult indicating success or failure reason
     */
    public MobileEmitterService.ValidationResult tryActivate() {
        if (!isPrimary || level == null || level.isClientSide) {
            return MobileEmitterService.ValidationResult.EMITTER_NOT_FOUND;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        // Validate the network
        MobileEmitterService.ValidationResult result = MobileEmitterService.validateNetwork(
            serverLevel, linkedPositions, networkId
        );

        if (result != MobileEmitterService.ValidationResult.VALID) {
            return result;
        }

        // Calculate interior bounds
        BlockPos[] bounds = MobileEmitterService.calculateInteriorBounds(linkedPositions, level);
        if (bounds == null) {
            return MobileEmitterService.ValidationResult.NOT_RECTANGULAR;
        }

        interiorMin = bounds[0];
        interiorMax = bounds[1];
        active = true;

        // Activate all emitters in the network
        for (BlockPos pos : linkedPositions) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.MOBILE_EMITTER.get())) {
                level.setBlock(pos, state.setValue(MobileEmitterBlock.ACTIVE, true), 3);
            }
        }

        setChanged();
        return MobileEmitterService.ValidationResult.VALID;
    }

    /**
     * Deactivate the network. Can be called on any emitter in the network.
     */
    public void deactivate() {
        if (level == null || level.isClientSide) return;

        // If this is not primary, find and deactivate the primary
        if (!isPrimary && networkId != null) {
            // Find primary emitter through linked emitters
            for (BlockPos pos : getAllNetworkPositions()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MobileEmitterBlockEntity emitter && emitter.isPrimary()) {
                    emitter.deactivateAsPrimary();
                    return;
                }
            }
        } else if (isPrimary) {
            deactivateAsPrimary();
        }
    }

    /**
     * Internal method to deactivate when called on primary emitter.
     */
    private void deactivateAsPrimary() {
        if (!isPrimary || level == null || level.isClientSide) return;

        ServerLevel serverLevel = (ServerLevel) level;

        // Restore all players inside
        for (UUID playerId : new HashSet<>(playersInside)) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) {
                onPlayerExit(player);
            }
        }

        // Clear interior blocks
        clearInterior(serverLevel);

        // Deactivate all emitters
        for (BlockPos pos : linkedPositions) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.MOBILE_EMITTER.get())) {
                level.setBlock(pos, state.setValue(MobileEmitterBlock.ACTIVE, false), 3);
            }
        }

        // Reset state
        interiorMin = null;
        interiorMax = null;
        active = false;
        playersInside.clear();
        originalGameModes.clear();
        originalInventories.clear();

        setChanged();
    }

    /**
     * Called when this emitter is removed from the world.
     */
    public void onRemoved() {
        if (level == null || level.isClientSide) return;

        if (active) {
            // Deactivate the network first
            if (isPrimary) {
                deactivateAsPrimary();
            } else {
                deactivate();
            }
        }

        // If this was part of a network, remove it
        if (networkId != null && !isPrimary) {
            // Find primary and remove this position
            for (BlockPos pos : getAllNetworkPositions()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MobileEmitterBlockEntity emitter && emitter.isPrimary()) {
                    emitter.removeLinkedPosition(worldPosition);
                    break;
                }
            }
        }
    }

    /**
     * Get all positions in this network (including self).
     */
    private List<BlockPos> getAllNetworkPositions() {
        if (isPrimary) {
            return new ArrayList<>(linkedPositions);
        }
        // For secondary, we need to find other network members through the level
        // This is a limitation - secondaries only know their network ID
        List<BlockPos> positions = new ArrayList<>();
        positions.add(worldPosition);
        return positions;
    }

    /**
     * Save a holoprogram from the current holo-area.
     */
    public HoloprogramManager.SaveResult saveHoloprogram(String name) {
        if (!active || !isPrimary || level == null || level.isClientSide) {
            return HoloprogramManager.SaveResult.failure();
        }
        if (interiorMin == null || interiorMax == null) {
            return HoloprogramManager.SaveResult.failure();
        }

        return HoloprogramManager.save((ServerLevel) level, name, interiorMin, interiorMax);
    }

    /**
     * Load a holoprogram into the holo-area.
     */
    public HoloprogramManager.LoadResultDetails loadHoloprogram(String name) {
        if (!active || !isPrimary || level == null || level.isClientSide) {
            return null;
        }
        if (interiorMin == null || interiorMax == null) {
            return null;
        }

        // Calculate interior size
        Vec3i interiorSize = new Vec3i(
            interiorMax.getX() - interiorMin.getX() + 1,
            interiorMax.getY() - interiorMin.getY() + 1,
            interiorMax.getZ() - interiorMin.getZ() + 1
        );

        // First clear the interior
        clearInterior((ServerLevel) level);

        // Then load the holoprogram
        return HoloprogramManager.load((ServerLevel) level, name, interiorMin, interiorSize);
    }

    /**
     * Manually clear the holo-area.
     */
    public void manualClear() {
        if (!active || !isPrimary || level == null || level.isClientSide) return;
        if (interiorMin == null || interiorMax == null) return;
        clearInterior((ServerLevel) level);
    }

    // Getters
    public UUID getNetworkId() { return networkId; }
    public boolean isPrimary() { return isPrimary; }
    public List<BlockPos> getLinkedPositions() { return new ArrayList<>(linkedPositions); }
    public boolean isActive() { return active; }
    public BlockPos getInteriorMin() { return interiorMin; }
    public BlockPos getInteriorMax() { return interiorMax; }
    public int getNetworkSize() { return isPrimary ? linkedPositions.size() : 0; }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        if (networkId != null) {
            tag.putUUID("NetworkId", networkId);
        }
        tag.putBoolean("IsPrimary", isPrimary);
        tag.putBoolean("Active", active);

        if (isPrimary) {
            // Save linked positions
            ListTag positionsList = new ListTag();
            for (BlockPos pos : linkedPositions) {
                CompoundTag posTag = new CompoundTag();
                posTag.putInt("X", pos.getX());
                posTag.putInt("Y", pos.getY());
                posTag.putInt("Z", pos.getZ());
                positionsList.add(posTag);
            }
            tag.put("LinkedPositions", positionsList);

            // Save interior bounds
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

            // Save player tracking data
            ListTag gameModesTag = new ListTag();
            for (Map.Entry<UUID, GameType> entry : originalGameModes.entrySet()) {
                CompoundTag modeTag = new CompoundTag();
                modeTag.putUUID("Player", entry.getKey());
                modeTag.putInt("GameMode", entry.getValue().getId());
                gameModesTag.add(modeTag);
            }
            tag.put("OriginalGameModes", gameModesTag);

            ListTag inventoriesTag = new ListTag();
            for (Map.Entry<UUID, ListTag> entry : originalInventories.entrySet()) {
                CompoundTag invTag = new CompoundTag();
                invTag.putUUID("Player", entry.getKey());
                invTag.put("Inventory", entry.getValue());
                inventoriesTag.add(invTag);
            }
            tag.put("OriginalInventories", inventoriesTag);

            ListTag playersTag = new ListTag();
            for (UUID playerId : playersInside) {
                CompoundTag playerTag = new CompoundTag();
                playerTag.putUUID("Player", playerId);
                playersTag.add(playerTag);
            }
            tag.put("PlayersInside", playersTag);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        if (tag.hasUUID("NetworkId")) {
            networkId = tag.getUUID("NetworkId");
        }
        isPrimary = tag.getBoolean("IsPrimary");
        active = tag.getBoolean("Active");

        if (isPrimary) {
            // Load linked positions
            linkedPositions.clear();
            if (tag.contains("LinkedPositions")) {
                ListTag positionsList = tag.getList("LinkedPositions", Tag.TAG_COMPOUND);
                for (int i = 0; i < positionsList.size(); i++) {
                    CompoundTag posTag = positionsList.getCompound(i);
                    linkedPositions.add(new BlockPos(
                        posTag.getInt("X"),
                        posTag.getInt("Y"),
                        posTag.getInt("Z")
                    ));
                }
            }

            // Load interior bounds
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

            // Load player tracking data
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

            playersInside.clear();
            if (tag.contains("PlayersInside")) {
                ListTag playersTag = tag.getList("PlayersInside", Tag.TAG_COMPOUND);
                for (int i = 0; i < playersTag.size(); i++) {
                    CompoundTag playerTag = playersTag.getCompound(i);
                    playersInside.add(playerTag.getUUID("Player"));
                }
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("Active", active);
        tag.putBoolean("IsPrimary", isPrimary);
        if (networkId != null) {
            tag.putUUID("NetworkId", networkId);
        }
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
