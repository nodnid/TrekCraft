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

import java.util.*;

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
                    data.rooms.put(pos, new RoomRecord(pos, cachedFuel, registeredTime));
                }
            }
        }
        // Migration: load old single-room format
        else if (tag.contains("RoomPos")) {
            int[] roomPosArray = tag.getIntArray("RoomPos");
            if (roomPosArray.length == 3) {
                BlockPos pos = new BlockPos(roomPosArray[0], roomPosArray[1], roomPosArray[2]);
                int cachedFuel = tag.getInt("CachedFuel");
                data.rooms.put(pos, new RoomRecord(pos, cachedFuel, System.currentTimeMillis()));
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
                data.pads.put(pos, new PadRecord(pos, name, created));
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

            data.signals.put(tricorderId, new SignalRecord(tricorderId, displayName, pos, lastSeen, type, holderId));
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

        return tag;
    }

    // ===== Room methods =====

    public void registerRoom(BlockPos pos) {
        rooms.put(pos, new RoomRecord(pos, 0, System.currentTimeMillis()));
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
     * Find the nearest room within the given range.
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
            rooms.put(roomPos, new RoomRecord(room.pos(), fuel, room.registeredTime()));
            setDirty();
        }
    }

    public boolean consumeRoomFuel(BlockPos roomPos, int amount) {
        RoomRecord room = rooms.get(roomPos);
        if (room != null && room.cachedFuel() >= amount) {
            rooms.put(roomPos, new RoomRecord(room.pos(), room.cachedFuel() - amount, room.registeredTime()));
            setDirty();
            return true;
        }
        return false;
    }

    // ===== Pad methods =====

    public void registerPad(BlockPos pos, String name) {
        pads.put(pos, new PadRecord(pos, name, System.currentTimeMillis()));
        setDirty();
    }

    public void unregisterPad(BlockPos pos) {
        pads.remove(pos);
        setDirty();
    }

    public Map<BlockPos, PadRecord> getPads() {
        return Collections.unmodifiableMap(pads);
    }

    public Optional<PadRecord> getPad(BlockPos pos) {
        return Optional.ofNullable(pads.get(pos));
    }

    // ===== Signal methods =====

    /**
     * Register a dropped tricorder signal.
     */
    public void registerDroppedSignal(UUID tricorderId, String displayName, BlockPos pos, long gameTime) {
        signals.put(tricorderId, new SignalRecord(tricorderId, displayName, pos, gameTime, SignalType.DROPPED, null));
        setDirty();
    }

    /**
     * Register a held tricorder signal (in player inventory).
     */
    public void registerHeldSignal(UUID tricorderId, String displayName, BlockPos pos, long gameTime, UUID holderId) {
        signals.put(tricorderId, new SignalRecord(tricorderId, displayName, pos, gameTime, SignalType.HELD, holderId));
        setDirty();
    }

    /**
     * Update signal position (for tracking held tricorders).
     */
    public void updateSignalPosition(UUID tricorderId, BlockPos pos, long gameTime) {
        SignalRecord existing = signals.get(tricorderId);
        if (existing != null) {
            signals.put(tricorderId, new SignalRecord(
                    existing.tricorderId(),
                    existing.displayName(),
                    pos,
                    gameTime,
                    existing.type(),
                    existing.holderId()
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
     * Get all unlinked wormholes in the specified dimension (excludes the given portal).
     */
    public List<WormholeRecord> getUnlinkedWormholes(String dimensionKey, UUID excludePortalId) {
        List<WormholeRecord> unlinked = new ArrayList<>();
        for (WormholeRecord wormhole : wormholes.values()) {
            if (!wormhole.isLinked()
                    && wormhole.dimensionKey().equals(dimensionKey)
                    && !wormhole.portalId().equals(excludePortalId)) {
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

    // ===== Record types =====

    public record RoomRecord(BlockPos pos, int cachedFuel, long registeredTime) {}

    public record PadRecord(BlockPos pos, String name, long createdGameTime) {}

    public record SignalRecord(
            UUID tricorderId,
            String displayName,
            BlockPos lastKnownPos,
            long lastSeenGameTime,
            SignalType type,
            @Nullable UUID holderId
    ) {}

    public enum SignalType {
        DROPPED,
        HELD
    }
}
