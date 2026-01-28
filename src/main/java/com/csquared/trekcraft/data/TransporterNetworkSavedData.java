package com.csquared.trekcraft.data;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

import java.util.*;
import java.util.stream.Collectors;

public class TransporterNetworkSavedData extends SavedData {
    private static final String DATA_NAME = TrekCraftMod.MODID + "_transporter_network";

    // Multiple Transporter Rooms
    private final Map<BlockPos, RoomRecord> rooms = new HashMap<>();

    // Registered pads
    private final Map<BlockPos, PadRecord> pads = new HashMap<>();

    // Tricorder signals (both held and dropped)
    private final Map<UUID, SignalRecord> signals = new HashMap<>();

    // Wormhole portals
    private final Map<UUID, WormholeRecord> wormholes = new HashMap<>();

    // Player contribution tracking
    private final Map<UUID, ContributorRecord> contributors = new HashMap<>();

    public TransporterNetworkSavedData() {
    }

    public static TransporterNetworkSavedData get(ServerLevel level) {
        // Always get from overworld to ensure single source of truth
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            overworld = level;
        }
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(TransporterNetworkSavedData::new, TransporterNetworkSavedData::load),
                DATA_NAME
        );
    }

    public static TransporterNetworkSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        TransporterNetworkSavedData data = new TransporterNetworkSavedData();

        // Load rooms - check for new format first
        if (tag.contains("Rooms", Tag.TAG_LIST)) {
            ListTag roomsTag = tag.getList("Rooms", Tag.TAG_COMPOUND);
            for (int i = 0; i < roomsTag.size(); i++) {
                CompoundTag roomTag = roomsTag.getCompound(i);
                int[] posArray = roomTag.getIntArray("Pos");
                if (posArray.length == 3) {
                    BlockPos pos = new BlockPos(posArray[0], posArray[1], posArray[2]);
                    int cachedFuel = roomTag.getInt("CachedFuel");
                    long registeredTime = roomTag.getLong("RegisteredTime");
                    // Migration: default to overworld if dimension not present
                    String dimensionKey = roomTag.contains("DimensionKey") ?
                            roomTag.getString("DimensionKey") : "minecraft:overworld";
                    data.rooms.put(pos, new RoomRecord(pos, cachedFuel, registeredTime, dimensionKey));
                }
            }
        }
        // Migration: load old single-room format
        else if (tag.contains("RoomPos")) {
            int[] roomPosArray = tag.getIntArray("RoomPos");
            if (roomPosArray.length == 3) {
                BlockPos pos = new BlockPos(roomPosArray[0], roomPosArray[1], roomPosArray[2]);
                int cachedFuel = tag.getInt("CachedFuel");
                data.rooms.put(pos, new RoomRecord(pos, cachedFuel, System.currentTimeMillis(), "minecraft:overworld"));
                TrekCraftMod.LOGGER.info("Migrated old single-room format to multi-room format");
            }
        }

        // Load pads
        ListTag padsTag = tag.getList("Pads", Tag.TAG_COMPOUND);
        for (int i = 0; i < padsTag.size(); i++) {
            CompoundTag padTag = padsTag.getCompound(i);
            int[] posArray = padTag.getIntArray("Pos");
            if (posArray.length == 3) {
                BlockPos pos = new BlockPos(posArray[0], posArray[1], posArray[2]);
                String name = padTag.getString("Name");
                long created = padTag.getLong("Created");
                // Migration: default to overworld if dimension not present
                String dimensionKey = padTag.contains("DimensionKey") ?
                        padTag.getString("DimensionKey") : "minecraft:overworld";
                data.pads.put(pos, new PadRecord(pos, name, created, dimensionKey));
            }
        }

        // Load signals - check for new format with signal type
        ListTag signalsTag = tag.getList("Signals", Tag.TAG_COMPOUND);
        for (int i = 0; i < signalsTag.size(); i++) {
            CompoundTag signalTag = signalsTag.getCompound(i);
            UUID tricorderId = signalTag.getUUID("TricorderId");
            String displayName = signalTag.getString("DisplayName");
            int[] posArray = signalTag.getIntArray("Pos");
            BlockPos pos = posArray.length == 3 ? new BlockPos(posArray[0], posArray[1], posArray[2]) : BlockPos.ZERO;
            long lastSeen = signalTag.getLong("LastSeen");

            // New fields
            SignalType type = SignalType.DROPPED; // Default for migration
            if (signalTag.contains("Type")) {
                try {
                    type = SignalType.valueOf(signalTag.getString("Type"));
                } catch (IllegalArgumentException ignored) {}
            }

            UUID holderId = null;
            if (signalTag.contains("HolderId")) {
                holderId = signalTag.getUUID("HolderId");
            }

            // Migration: default to overworld if dimension not present
            String dimensionKey = signalTag.contains("DimensionKey") ?
                    signalTag.getString("DimensionKey") : "minecraft:overworld";

            data.signals.put(tricorderId, new SignalRecord(tricorderId, displayName, pos, lastSeen, type, holderId, dimensionKey));
        }

        // Load wormholes
        if (tag.contains("Wormholes", Tag.TAG_LIST)) {
            ListTag wormholesTag = tag.getList("Wormholes", Tag.TAG_COMPOUND);
            for (int i = 0; i < wormholesTag.size(); i++) {
                CompoundTag wormholeTag = wormholesTag.getCompound(i);
                UUID portalId = wormholeTag.getUUID("PortalId");
                String name = wormholeTag.getString("Name");
                int[] anchorArray = wormholeTag.getIntArray("AnchorPos");
                BlockPos anchorPos = anchorArray.length == 3 ?
                        new BlockPos(anchorArray[0], anchorArray[1], anchorArray[2]) : BlockPos.ZERO;
                Direction.Axis axis = Direction.Axis.valueOf(wormholeTag.getString("Axis"));
                int width = wormholeTag.getInt("Width");
                int height = wormholeTag.getInt("Height");
                UUID linkedPortalId = wormholeTag.contains("LinkedPortalId") ?
                        wormholeTag.getUUID("LinkedPortalId") : null;
                long createdTime = wormholeTag.getLong("CreatedTime");
                String dimensionKey = wormholeTag.getString("DimensionKey");

                data.wormholes.put(portalId, new WormholeRecord(
                        portalId, name, anchorPos, axis, width, height, linkedPortalId, createdTime, dimensionKey
                ));
            }
        }

        // Load contributors
        if (tag.contains("Contributors", Tag.TAG_LIST)) {
            ListTag contributorsTag = tag.getList("Contributors", Tag.TAG_COMPOUND);
            for (int i = 0; i < contributorsTag.size(); i++) {
                CompoundTag contribTag = contributorsTag.getCompound(i);
                UUID playerId = contribTag.getUUID("PlayerId");
                String lastKnownName = contribTag.getString("LastKnownName");
                long totalDeposited = contribTag.getLong("TotalDeposited");
                long totalWithdrawn = contribTag.getLong("TotalWithdrawn");
                long lastActivityTime = contribTag.getLong("LastActivityTime");
                int freeTransportsUsed = contribTag.getInt("FreeTransportsUsed");
                ContributorRank highestRank = ContributorRank.CREWMAN;
                if (contribTag.contains("HighestRank")) {
                    try {
                        highestRank = ContributorRank.valueOf(contribTag.getString("HighestRank"));
                    } catch (IllegalArgumentException ignored) {}
                }
                data.contributors.put(playerId, new ContributorRecord(
                        playerId, lastKnownName, totalDeposited, totalWithdrawn,
                        lastActivityTime, freeTransportsUsed, highestRank
                ));
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // Save rooms
        ListTag roomsTag = new ListTag();
        for (RoomRecord room : rooms.values()) {
            CompoundTag roomTag = new CompoundTag();
            roomTag.putIntArray("Pos", new int[]{room.pos().getX(), room.pos().getY(), room.pos().getZ()});
            roomTag.putInt("CachedFuel", room.cachedFuel());
            roomTag.putLong("RegisteredTime", room.registeredTime());
            roomTag.putString("DimensionKey", room.dimensionKey());
            roomsTag.add(roomTag);
        }
        tag.put("Rooms", roomsTag);

        // Save pads
        ListTag padsTag = new ListTag();
        for (PadRecord pad : pads.values()) {
            CompoundTag padTag = new CompoundTag();
            padTag.putIntArray("Pos", new int[]{pad.pos().getX(), pad.pos().getY(), pad.pos().getZ()});
            padTag.putString("Name", pad.name());
            padTag.putLong("Created", pad.createdGameTime());
            padTag.putString("DimensionKey", pad.dimensionKey());
            padsTag.add(padTag);
        }
        tag.put("Pads", padsTag);

        // Save signals
        ListTag signalsTag = new ListTag();
        for (SignalRecord signal : signals.values()) {
            CompoundTag signalTag = new CompoundTag();
            signalTag.putUUID("TricorderId", signal.tricorderId());
            signalTag.putString("DisplayName", signal.displayName());
            signalTag.putIntArray("Pos", new int[]{
                    signal.lastKnownPos().getX(),
                    signal.lastKnownPos().getY(),
                    signal.lastKnownPos().getZ()
            });
            signalTag.putLong("LastSeen", signal.lastSeenGameTime());
            signalTag.putString("Type", signal.type().name());
            if (signal.holderId() != null) {
                signalTag.putUUID("HolderId", signal.holderId());
            }
            signalTag.putString("DimensionKey", signal.dimensionKey());
            signalsTag.add(signalTag);
        }
        tag.put("Signals", signalsTag);

        // Save wormholes
        ListTag wormholesTag = new ListTag();
        for (WormholeRecord wormhole : wormholes.values()) {
            CompoundTag wormholeTag = new CompoundTag();
            wormholeTag.putUUID("PortalId", wormhole.portalId());
            wormholeTag.putString("Name", wormhole.name());
            wormholeTag.putIntArray("AnchorPos", new int[]{
                    wormhole.anchorPos().getX(),
                    wormhole.anchorPos().getY(),
                    wormhole.anchorPos().getZ()
            });
            wormholeTag.putString("Axis", wormhole.axis().name());
            wormholeTag.putInt("Width", wormhole.width());
            wormholeTag.putInt("Height", wormhole.height());
            if (wormhole.linkedPortalId() != null) {
                wormholeTag.putUUID("LinkedPortalId", wormhole.linkedPortalId());
            }
            wormholeTag.putLong("CreatedTime", wormhole.createdTime());
            wormholeTag.putString("DimensionKey", wormhole.dimensionKey());
            wormholesTag.add(wormholeTag);
        }
        tag.put("Wormholes", wormholesTag);

        // Save contributors
        ListTag contributorsTag = new ListTag();
        for (ContributorRecord contrib : contributors.values()) {
            CompoundTag contribTag = new CompoundTag();
            contribTag.putUUID("PlayerId", contrib.playerId());
            contribTag.putString("LastKnownName", contrib.lastKnownName());
            contribTag.putLong("TotalDeposited", contrib.totalDeposited());
            contribTag.putLong("TotalWithdrawn", contrib.totalWithdrawn());
            contribTag.putLong("LastActivityTime", contrib.lastActivityTime());
            contribTag.putInt("FreeTransportsUsed", contrib.freeTransportsUsed());
            contribTag.putString("HighestRank", contrib.highestRankAchieved().name());
            contributorsTag.add(contribTag);
        }
        tag.put("Contributors", contributorsTag);

        return tag;
    }

    // ===== Room methods =====

    public void registerRoom(BlockPos pos, String dimensionKey) {
        rooms.put(pos, new RoomRecord(pos, 0, System.currentTimeMillis(), dimensionKey));
        setDirty();
    }

    public void unregisterRoom(BlockPos pos) {
        rooms.remove(pos);
        setDirty();
    }

    public boolean hasAnyRoom() {
        return !rooms.isEmpty();
    }

    public Map<BlockPos, RoomRecord> getRooms() {
        return Collections.unmodifiableMap(rooms);
    }

    public Optional<RoomRecord> getRoom(BlockPos pos) {
        return Optional.ofNullable(rooms.get(pos));
    }

    /**
     * Find the nearest room within the given range (any dimension).
     * @param playerPos The player's current position
     * @param maxRange Maximum distance to search
     * @return The nearest room within range, or empty if none found
     */
    public Optional<RoomRecord> getNearestRoom(BlockPos playerPos, double maxRange) {
        RoomRecord nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (RoomRecord room : rooms.values()) {
            double distSq = playerPos.distSqr(room.pos());
            if (distSq <= maxRange * maxRange && distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = room;
            }
        }

        return Optional.ofNullable(nearest);
    }

    /**
     * Find the nearest room within the given range in a specific dimension.
     * @param playerPos The player's current position
     * @param dimensionKey The dimension to search in
     * @param maxRange Maximum distance to search
     * @return The nearest room within range in the dimension, or empty if none found
     */
    public Optional<RoomRecord> getNearestRoomInDimension(BlockPos playerPos, String dimensionKey, double maxRange) {
        RoomRecord nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (RoomRecord room : rooms.values()) {
            if (!room.dimensionKey().equals(dimensionKey)) {
                continue;
            }
            double distSq = playerPos.distSqr(room.pos());
            if (distSq <= maxRange * maxRange && distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = room;
            }
        }

        return Optional.ofNullable(nearest);
    }

    /**
     * Get the total fuel across all rooms in the network.
     */
    public int getTotalNetworkFuel() {
        return rooms.values().stream().mapToInt(RoomRecord::cachedFuel).sum();
    }

    public int getRoomFuel(BlockPos roomPos) {
        RoomRecord room = rooms.get(roomPos);
        return room != null ? room.cachedFuel() : 0;
    }

    public void setRoomFuel(BlockPos roomPos, int fuel) {
        RoomRecord room = rooms.get(roomPos);
        if (room != null) {
            rooms.put(roomPos, new RoomRecord(room.pos(), fuel, room.registeredTime(), room.dimensionKey()));
            setDirty();
        }
    }

    public boolean consumeRoomFuel(BlockPos roomPos, int amount) {
        RoomRecord room = rooms.get(roomPos);
        if (room != null && room.cachedFuel() >= amount) {
            rooms.put(roomPos, new RoomRecord(room.pos(), room.cachedFuel() - amount, room.registeredTime(), room.dimensionKey()));
            setDirty();
            return true;
        }
        return false;
    }

    // ===== Pad methods =====

    public void registerPad(BlockPos pos, String name, String dimensionKey) {
        pads.put(pos, new PadRecord(pos, name, System.currentTimeMillis(), dimensionKey));
        setDirty();
    }

    public void unregisterPad(BlockPos pos) {
        pads.remove(pos);
        setDirty();
    }

    public Map<BlockPos, PadRecord> getPads() {
        return Collections.unmodifiableMap(pads);
    }

    /**
     * Get all pads in a specific dimension.
     */
    public Map<BlockPos, PadRecord> getPadsInDimension(String dimensionKey) {
        Map<BlockPos, PadRecord> result = new HashMap<>();
        for (var entry : pads.entrySet()) {
            if (entry.getValue().dimensionKey().equals(dimensionKey)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public Optional<PadRecord> getPad(BlockPos pos) {
        return Optional.ofNullable(pads.get(pos));
    }

    // ===== Signal methods =====

    /**
     * Register a dropped tricorder signal.
     */
    public void registerDroppedSignal(UUID tricorderId, String displayName, BlockPos pos, long gameTime, String dimensionKey) {
        signals.put(tricorderId, new SignalRecord(tricorderId, displayName, pos, gameTime, SignalType.DROPPED, null, dimensionKey));
        setDirty();
    }

    /**
     * Register a held tricorder signal (in player inventory).
     */
    public void registerHeldSignal(UUID tricorderId, String displayName, BlockPos pos, long gameTime, UUID holderId, String dimensionKey) {
        signals.put(tricorderId, new SignalRecord(tricorderId, displayName, pos, gameTime, SignalType.HELD, holderId, dimensionKey));
        setDirty();
    }

    /**
     * Update signal position (for tracking held tricorders).
     */
    public void updateSignalPosition(UUID tricorderId, BlockPos pos, long gameTime, String dimensionKey) {
        SignalRecord existing = signals.get(tricorderId);
        if (existing != null) {
            signals.put(tricorderId, new SignalRecord(
                    existing.tricorderId(),
                    existing.displayName(),
                    pos,
                    gameTime,
                    existing.type(),
                    existing.holderId(),
                    dimensionKey
            ));
            setDirty();
        }
    }

    public void unregisterSignal(UUID tricorderId) {
        signals.remove(tricorderId);
        setDirty();
    }

    public Map<UUID, SignalRecord> getSignals() {
        return Collections.unmodifiableMap(signals);
    }

    /**
     * Get all signals in a specific dimension.
     */
    public Map<UUID, SignalRecord> getSignalsInDimension(String dimensionKey) {
        Map<UUID, SignalRecord> result = new HashMap<>();
        for (var entry : signals.entrySet()) {
            if (entry.getValue().dimensionKey().equals(dimensionKey)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public Optional<SignalRecord> getSignal(UUID tricorderId) {
        return Optional.ofNullable(signals.get(tricorderId));
    }

    // ===== Wormhole methods =====

    /**
     * Register a new wormhole portal.
     */
    public void registerWormhole(WormholeRecord wormhole) {
        wormholes.put(wormhole.portalId(), wormhole);
        setDirty();
    }

    /**
     * Unregister a wormhole portal.
     */
    public void unregisterWormhole(UUID portalId) {
        wormholes.remove(portalId);
        setDirty();
    }

    /**
     * Get a wormhole by its ID.
     */
    public Optional<WormholeRecord> getWormhole(UUID portalId) {
        return Optional.ofNullable(wormholes.get(portalId));
    }

    /**
     * Get all wormholes.
     */
    public Map<UUID, WormholeRecord> getWormholes() {
        return Collections.unmodifiableMap(wormholes);
    }

    /**
     * Get unlinked wormholes, optionally filtered by dimension.
     * @param dimensionKey If null, returns all unlinked wormholes; otherwise filters by dimension
     * @param excludePortalId Portal ID to exclude from results
     */
    public List<WormholeRecord> getUnlinkedWormholes(@Nullable String dimensionKey, UUID excludePortalId) {
        List<WormholeRecord> unlinked = new ArrayList<>();
        for (WormholeRecord wormhole : wormholes.values()) {
            if (!wormhole.isLinked()
                    && !wormhole.portalId().equals(excludePortalId)
                    && (dimensionKey == null || wormhole.dimensionKey().equals(dimensionKey))) {
                unlinked.add(wormhole);
            }
        }
        return unlinked;
    }

    /**
     * Update a wormhole record (e.g., after linking or renaming).
     */
    public void updateWormhole(WormholeRecord wormhole) {
        wormholes.put(wormhole.portalId(), wormhole);
        setDirty();
    }

    /**
     * Find a wormhole by its anchor position.
     */
    public Optional<WormholeRecord> getWormholeByAnchor(BlockPos anchorPos) {
        for (WormholeRecord wormhole : wormholes.values()) {
            if (wormhole.anchorPos().equals(anchorPos)) {
                return Optional.of(wormhole);
            }
        }
        return Optional.empty();
    }

    /**
     * Find a wormhole by a position that is part of its cobblestone frame.
     * The frame surrounds the portal interior (anchorPos is bottom-left interior block).
     */
    public Optional<WormholeRecord> getWormholeByFramePosition(BlockPos framePos) {
        for (WormholeRecord wormhole : wormholes.values()) {
            if (isPositionInFrame(framePos, wormhole)) {
                return Optional.of(wormhole);
            }
        }
        return Optional.empty();
    }

    /**
     * Check if a position is part of a wormhole's frame.
     */
    private boolean isPositionInFrame(BlockPos pos, WormholeRecord wormhole) {
        BlockPos anchor = wormhole.anchorPos();
        Direction horizontal = wormhole.axis() == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
        int width = wormhole.width();
        int height = wormhole.height();

        // Frame bottom-left corner is one below and one back from anchor
        BlockPos bottomLeft = anchor.below().relative(horizontal.getOpposite());

        // Check bottom row
        for (int i = 0; i < width + 2; i++) {
            if (pos.equals(bottomLeft.relative(horizontal, i))) {
                return true;
            }
        }

        // Check top row
        BlockPos topLeft = bottomLeft.above(height + 1);
        for (int i = 0; i < width + 2; i++) {
            if (pos.equals(topLeft.relative(horizontal, i))) {
                return true;
            }
        }

        // Check left side (excluding corners)
        for (int i = 1; i <= height; i++) {
            if (pos.equals(bottomLeft.above(i))) {
                return true;
            }
        }

        // Check right side (excluding corners)
        BlockPos bottomRight = bottomLeft.relative(horizontal, width + 1);
        for (int i = 1; i <= height; i++) {
            if (pos.equals(bottomRight.above(i))) {
                return true;
            }
        }

        return false;
    }

    // ===== Record types =====

    public record RoomRecord(BlockPos pos, int cachedFuel, long registeredTime, String dimensionKey) {}

    public record PadRecord(BlockPos pos, String name, long createdGameTime, String dimensionKey) {}

    public record SignalRecord(
            UUID tricorderId,
            String displayName,
            BlockPos lastKnownPos,
            long lastSeenGameTime,
            SignalType type,
            @Nullable UUID holderId,
            String dimensionKey
    ) {}

    public enum SignalType {
        DROPPED,
        HELD
    }

    public record ContributorRecord(
            UUID playerId,
            String lastKnownName,
            long totalDeposited,
            long totalWithdrawn,
            long lastActivityTime,
            int freeTransportsUsed,
            ContributorRank highestRankAchieved
    ) {
        /**
         * Net contribution = deposited - withdrawn
         */
        public long getNetContribution() {
            return totalDeposited - totalWithdrawn;
        }

        /**
         * Free transports = net / 10 - used
         */
        public int getFreeTransportsRemaining() {
            return Math.max(0, (int)(getNetContribution() / 10) - freeTransportsUsed);
        }

        /**
         * Get total earned free transports (net / 10)
         */
        public int getTotalFreeTransportsEarned() {
            return (int)(getNetContribution() / 10);
        }
    }

    // ===== Contributor methods =====

    /**
     * Record a deposit of latinum strips by a player.
     */
    public void recordDeposit(UUID playerId, String playerName, int amount) {
        ContributorRecord existing = contributors.get(playerId);
        if (existing == null) {
            contributors.put(playerId, new ContributorRecord(
                    playerId, playerName, amount, 0,
                    System.currentTimeMillis(), 0, ContributorRank.CREWMAN
            ));
        } else {
            contributors.put(playerId, new ContributorRecord(
                    playerId, playerName,
                    existing.totalDeposited() + amount,
                    existing.totalWithdrawn(),
                    System.currentTimeMillis(),
                    existing.freeTransportsUsed(),
                    existing.highestRankAchieved()
            ));
        }
        setDirty();
    }

    /**
     * Record a withdrawal of latinum strips by a player.
     */
    public void recordWithdrawal(UUID playerId, String playerName, int amount) {
        ContributorRecord existing = contributors.get(playerId);
        if (existing == null) {
            contributors.put(playerId, new ContributorRecord(
                    playerId, playerName, 0, amount,
                    System.currentTimeMillis(), 0, ContributorRank.CREWMAN
            ));
        } else {
            contributors.put(playerId, new ContributorRecord(
                    playerId, playerName,
                    existing.totalDeposited(),
                    existing.totalWithdrawn() + amount,
                    System.currentTimeMillis(),
                    existing.freeTransportsUsed(),
                    existing.highestRankAchieved()
            ));
        }
        setDirty();
    }

    /**
     * Get a contributor record by player ID.
     */
    public Optional<ContributorRecord> getContributor(UUID playerId) {
        return Optional.ofNullable(contributors.get(playerId));
    }

    /**
     * Consume a free transport for a player.
     * @return true if a free transport was available and consumed, false otherwise
     */
    public boolean consumeFreeTransport(UUID playerId) {
        ContributorRecord existing = contributors.get(playerId);
        if (existing == null || existing.getFreeTransportsRemaining() <= 0) {
            return false;
        }

        contributors.put(playerId, new ContributorRecord(
                existing.playerId(),
                existing.lastKnownName(),
                existing.totalDeposited(),
                existing.totalWithdrawn(),
                existing.lastActivityTime(),
                existing.freeTransportsUsed() + 1,
                existing.highestRankAchieved()
        ));
        setDirty();
        return true;
    }

    /**
     * Get top contributors sorted by net contribution.
     */
    public List<ContributorRecord> getTopContributors(int limit) {
        return contributors.values().stream()
                .sorted((a, b) -> Long.compare(b.getNetContribution(), a.getNetContribution()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Check if a player has achieved a new rank and award diamonds if so.
     * Should be called after recordDeposit.
     * @return the new rank if promoted, null if no promotion
     */
    public ContributorRank checkAndAwardRankUp(UUID playerId, ServerPlayer player) {
        ContributorRecord record = contributors.get(playerId);
        if (record == null) {
            return null;
        }

        ContributorRank currentRank = ContributorRank.forContribution(record.getNetContribution());
        ContributorRank previousHighest = record.highestRankAchieved();

        // Check if we've achieved a new highest rank
        if (currentRank.ordinal() > previousHighest.ordinal()) {
            // Update the highest rank achieved
            contributors.put(playerId, new ContributorRecord(
                    record.playerId(),
                    record.lastKnownName(),
                    record.totalDeposited(),
                    record.totalWithdrawn(),
                    record.lastActivityTime(),
                    record.freeTransportsUsed(),
                    currentRank
            ));
            setDirty();

            // Award diamonds for the rank-up
            int diamondReward = currentRank.getDiamondReward();
            if (diamondReward > 0 && player != null) {
                ItemStack diamonds = new ItemStack(Items.DIAMOND, diamondReward);
                if (!player.getInventory().add(diamonds)) {
                    // Drop at player's feet if inventory is full
                    ItemEntity itemEntity = new ItemEntity(
                            player.level(),
                            player.getX(), player.getY(), player.getZ(),
                            diamonds
                    );
                    player.level().addFreshEntity(itemEntity);
                }

                // Send rank-up message
                player.sendSystemMessage(
                        Component.literal("★ PROMOTION ★ ")
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                                .append(Component.literal("You have achieved the rank of ")
                                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                                .append(Component.literal(currentRank.getTitle())
                                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                                .append(Component.literal("!")
                                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                );
                player.sendSystemMessage(
                        Component.literal("Reward: ")
                                .withStyle(ChatFormatting.GRAY)
                                .append(Component.literal(diamondReward + " Diamond" + (diamondReward > 1 ? "s" : ""))
                                        .withStyle(ChatFormatting.AQUA))
                );
            }

            return currentRank;
        }

        return null;
    }

    /**
     * Get all contributors.
     */
    public Map<UUID, ContributorRecord> getContributors() {
        return Collections.unmodifiableMap(contributors);
    }
}
