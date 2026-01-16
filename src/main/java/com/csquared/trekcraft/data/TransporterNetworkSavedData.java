package com.csquared.trekcraft.data;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class TransporterNetworkSavedData extends SavedData {
    private static final String DATA_NAME = TrekCraftMod.MODID + "_transporter_network";

    // Transporter Room (global controller)
    private BlockPos transporterRoomPos = null;
    private int cachedFuel = 0;

    // Registered pads
    private final Map<BlockPos, PadRecord> pads = new HashMap<>();

    // Dropped tricorder signals
    private final Map<UUID, SignalRecord> signals = new HashMap<>();

    // Away team requests
    private final Map<UUID, RequestRecord> requests = new HashMap<>();

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

        // Load transporter room
        if (tag.contains("RoomPos")) {
            int[] roomPosArray = tag.getIntArray("RoomPos");
            if (roomPosArray.length == 3) {
                data.transporterRoomPos = new BlockPos(roomPosArray[0], roomPosArray[1], roomPosArray[2]);
            }
        }
        data.cachedFuel = tag.getInt("CachedFuel");

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

        // Load signals
        ListTag signalsTag = tag.getList("Signals", Tag.TAG_COMPOUND);
        for (int i = 0; i < signalsTag.size(); i++) {
            CompoundTag signalTag = signalsTag.getCompound(i);
            UUID tricorderId = signalTag.getUUID("TricorderId");
            String displayName = signalTag.getString("DisplayName");
            int[] posArray = signalTag.getIntArray("Pos");
            BlockPos pos = posArray.length == 3 ? new BlockPos(posArray[0], posArray[1], posArray[2]) : BlockPos.ZERO;
            long lastSeen = signalTag.getLong("LastSeen");
            data.signals.put(tricorderId, new SignalRecord(tricorderId, displayName, pos, lastSeen));
        }

        // Load requests
        ListTag requestsTag = tag.getList("Requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < requestsTag.size(); i++) {
            CompoundTag reqTag = requestsTag.getCompound(i);
            UUID requester = reqTag.getUUID("Requester");
            UUID recipient = reqTag.getUUID("Recipient");
            int[] posArray = reqTag.getIntArray("AnchorPos");
            BlockPos anchorPos = posArray.length == 3 ? new BlockPos(posArray[0], posArray[1], posArray[2]) : BlockPos.ZERO;
            long created = reqTag.getLong("Created");
            long expires = reqTag.getLong("Expires");
            RequestStatus status = RequestStatus.valueOf(reqTag.getString("Status"));
            data.requests.put(recipient, new RequestRecord(requester, recipient, anchorPos, created, expires, status));
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // Save transporter room
        if (transporterRoomPos != null) {
            tag.putIntArray("RoomPos", new int[]{
                    transporterRoomPos.getX(),
                    transporterRoomPos.getY(),
                    transporterRoomPos.getZ()
            });
        }
        tag.putInt("CachedFuel", cachedFuel);

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
            signalsTag.add(signalTag);
        }
        tag.put("Signals", signalsTag);

        // Save requests
        ListTag requestsTag = new ListTag();
        for (RequestRecord req : requests.values()) {
            CompoundTag reqTag = new CompoundTag();
            reqTag.putUUID("Requester", req.requester());
            reqTag.putUUID("Recipient", req.recipient());
            reqTag.putIntArray("AnchorPos", new int[]{
                    req.requesterAnchorPos().getX(),
                    req.requesterAnchorPos().getY(),
                    req.requesterAnchorPos().getZ()
            });
            reqTag.putLong("Created", req.createdGameTime());
            reqTag.putLong("Expires", req.expiresGameTime());
            reqTag.putString("Status", req.status().name());
            requestsTag.add(reqTag);
        }
        tag.put("Requests", requestsTag);

        return tag;
    }

    // Transporter Room methods
    public boolean hasTransporterRoom() {
        return transporterRoomPos != null;
    }

    public BlockPos getTransporterRoomPos() {
        return transporterRoomPos;
    }

    public void setTransporterRoom(BlockPos pos) {
        this.transporterRoomPos = pos;
        this.cachedFuel = 0; // Will be updated by block entity
        setDirty();
    }

    public void clearTransporterRoom() {
        this.transporterRoomPos = null;
        this.cachedFuel = 0;
        setDirty();
    }

    public int getCachedFuel() {
        return cachedFuel;
    }

    public void setCachedFuel(int fuel) {
        this.cachedFuel = fuel;
        setDirty();
    }

    public boolean consumeFuel(int amount) {
        if (cachedFuel >= amount) {
            cachedFuel -= amount;
            setDirty();
            return true;
        }
        return false;
    }

    // Pad methods
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

    // Signal methods
    public void registerSignal(UUID tricorderId, String displayName, BlockPos pos, long gameTime) {
        signals.put(tricorderId, new SignalRecord(tricorderId, displayName, pos, gameTime));
        setDirty();
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

    // Request methods
    public void addRequest(RequestRecord request) {
        requests.put(request.recipient(), request);
        setDirty();
    }

    public void removeRequest(UUID recipientId) {
        requests.remove(recipientId);
        setDirty();
    }

    public Optional<RequestRecord> getRequestForRecipient(UUID recipientId) {
        return Optional.ofNullable(requests.get(recipientId));
    }

    public Map<UUID, RequestRecord> getRequests() {
        return Collections.unmodifiableMap(requests);
    }

    // Record types
    public record PadRecord(BlockPos pos, String name, long createdGameTime) {}

    public record SignalRecord(UUID tricorderId, String displayName, BlockPos lastKnownPos, long lastSeenGameTime) {}

    public record RequestRecord(
            UUID requester,
            UUID recipient,
            BlockPos requesterAnchorPos,
            long createdGameTime,
            long expiresGameTime,
            RequestStatus status
    ) {
        public RequestRecord withStatus(RequestStatus newStatus) {
            return new RequestRecord(requester, recipient, requesterAnchorPos, createdGameTime, expiresGameTime, newStatus);
        }

        public RequestRecord extendExpiration(long newExpires) {
            return new RequestRecord(requester, recipient, requesterAnchorPos, createdGameTime, newExpires, status);
        }
    }

    public enum RequestStatus {
        PENDING,
        HELD
    }
}
