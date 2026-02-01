package com.csquared.trekcraft.data;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.mission.Mission;
import com.csquared.trekcraft.mission.MissionStatus;
import com.csquared.trekcraft.starfleet.StarfleetRank;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SavedData for Starfleet missions and player XP tracking.
 * Follows TransporterNetworkSavedData pattern - routes to Overworld for single source of truth.
 */
public class StarfleetSavedData extends SavedData {
    private static final String DATA_NAME = TrekCraftMod.MODID + "_starfleet";

    // Player XP and mission history tracking
    private final Map<UUID, PlayerStarfleetRecord> players = new HashMap<>();

    // Active and historical missions
    private final Map<UUID, Mission> missions = new HashMap<>();

    // Manual admiral assignments (in addition to server ops)
    private final Set<UUID> admirals = new HashSet<>();

    // Flag for whether tutorial missions have been generated
    private boolean tutorialsGenerated = false;

    public StarfleetSavedData() {
    }

    public static StarfleetSavedData get(ServerLevel level) {
        // Always get from overworld to ensure single source of truth
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            overworld = level;
        }
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(StarfleetSavedData::new, StarfleetSavedData::load),
                DATA_NAME
        );
    }

    public static StarfleetSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        StarfleetSavedData data = new StarfleetSavedData();

        // Load players
        if (tag.contains("Players", Tag.TAG_LIST)) {
            ListTag playerList = tag.getList("Players", Tag.TAG_COMPOUND);
            for (int i = 0; i < playerList.size(); i++) {
                PlayerStarfleetRecord record = PlayerStarfleetRecord.load(playerList.getCompound(i));
                data.players.put(record.playerId(), record);
            }
        }

        // Load missions
        if (tag.contains("Missions", Tag.TAG_LIST)) {
            ListTag missionList = tag.getList("Missions", Tag.TAG_COMPOUND);
            for (int i = 0; i < missionList.size(); i++) {
                try {
                    Mission mission = Mission.load(missionList.getCompound(i));
                    if (mission != null) {
                        data.missions.put(mission.missionId(), mission);
                    }
                } catch (Exception e) {
                    TrekCraftMod.LOGGER.warn("Failed to load mission: {}", e.getMessage());
                }
            }
        }

        // Load admirals
        if (tag.contains("Admirals", Tag.TAG_LIST)) {
            ListTag admiralList = tag.getList("Admirals", Tag.TAG_COMPOUND);
            for (int i = 0; i < admiralList.size(); i++) {
                data.admirals.add(admiralList.getCompound(i).getUUID("PlayerId"));
            }
        }

        data.tutorialsGenerated = tag.getBoolean("TutorialsGenerated");

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        // Save players
        ListTag playerList = new ListTag();
        for (PlayerStarfleetRecord record : players.values()) {
            playerList.add(record.save());
        }
        tag.put("Players", playerList);

        // Save missions
        ListTag missionList = new ListTag();
        for (Mission mission : missions.values()) {
            missionList.add(mission.save());
        }
        tag.put("Missions", missionList);

        // Save admirals
        ListTag admiralList = new ListTag();
        for (UUID admiralId : admirals) {
            CompoundTag admiralTag = new CompoundTag();
            admiralTag.putUUID("PlayerId", admiralId);
            admiralList.add(admiralTag);
        }
        tag.put("Admirals", admiralList);

        tag.putBoolean("TutorialsGenerated", tutorialsGenerated);

        return tag;
    }

    // ===== Player Methods =====

    /**
     * Get or create a player's Starfleet record.
     */
    public PlayerStarfleetRecord getOrCreatePlayer(UUID playerId, String playerName) {
        PlayerStarfleetRecord record = players.get(playerId);
        if (record == null) {
            record = new PlayerStarfleetRecord(playerId, playerName, 0, new ArrayList<>(), System.currentTimeMillis());
            players.put(playerId, record);
            setDirty();
        }
        return record;
    }

    /**
     * Get a player's record if it exists.
     */
    public Optional<PlayerStarfleetRecord> getPlayer(UUID playerId) {
        return Optional.ofNullable(players.get(playerId));
    }

    /**
     * Award XP to a player.
     * @return The new rank if promoted, null otherwise
     */
    public StarfleetRank awardXp(UUID playerId, String playerName, int xp) {
        PlayerStarfleetRecord oldRecord = getOrCreatePlayer(playerId, playerName);
        StarfleetRank oldRank = StarfleetRank.fromXp(oldRecord.totalXp());

        PlayerStarfleetRecord newRecord = oldRecord.withXp(oldRecord.totalXp() + xp).withName(playerName);
        players.put(playerId, newRecord);
        setDirty();

        StarfleetRank newRank = StarfleetRank.fromXp(newRecord.totalXp());
        return newRank.ordinal() > oldRank.ordinal() ? newRank : null;
    }

    /**
     * Set a player's XP directly (admin command).
     */
    public void setXp(UUID playerId, String playerName, long xp) {
        PlayerStarfleetRecord record = getOrCreatePlayer(playerId, playerName);
        players.put(playerId, record.withXp(xp).withName(playerName));
        setDirty();
    }

    /**
     * Get a player's current rank.
     */
    public StarfleetRank getPlayerRank(UUID playerId) {
        PlayerStarfleetRecord record = players.get(playerId);
        if (record == null) {
            return StarfleetRank.CREWMAN;
        }
        // Check if they're an admiral first
        if (admirals.contains(playerId)) {
            return StarfleetRank.ADMIRAL;
        }
        return StarfleetRank.fromXp(record.totalXp());
    }

    /**
     * Get a player's total XP.
     */
    public long getPlayerXp(UUID playerId) {
        PlayerStarfleetRecord record = players.get(playerId);
        return record != null ? record.totalXp() : 0;
    }

    /**
     * Add a completed mission to a player's history.
     */
    public void addMissionToHistory(UUID playerId, String playerName, UUID missionId) {
        PlayerStarfleetRecord record = getOrCreatePlayer(playerId, playerName);
        players.put(playerId, record.withMission(missionId));
        setDirty();
    }

    // ===== Admiral Methods =====

    /**
     * Check if a player is an admiral.
     */
    public boolean isAdmiral(UUID playerId) {
        return admirals.contains(playerId);
    }

    /**
     * Grant admiral status to a player.
     */
    public void grantAdmiral(UUID playerId) {
        if (admirals.add(playerId)) {
            setDirty();
        }
    }

    /**
     * Revoke admiral status from a player.
     */
    public void revokeAdmiral(UUID playerId) {
        if (admirals.remove(playerId)) {
            setDirty();
        }
    }

    /**
     * Get all manual admirals.
     */
    public Set<UUID> getAdmirals() {
        return Collections.unmodifiableSet(admirals);
    }

    // ===== Mission Methods =====

    /**
     * Add a new mission.
     */
    public void addMission(Mission mission) {
        missions.put(mission.missionId(), mission);
        setDirty();
    }

    /**
     * Get a mission by ID.
     */
    public Optional<Mission> getMission(UUID missionId) {
        return Optional.ofNullable(missions.get(missionId));
    }

    /**
     * Update a mission.
     */
    public void updateMission(Mission mission) {
        missions.put(mission.missionId(), mission);
        setDirty();
    }

    /**
     * Remove a mission.
     */
    public void removeMission(UUID missionId) {
        if (missions.remove(missionId) != null) {
            setDirty();
        }
    }

    /**
     * Get all missions with a given status.
     */
    public List<Mission> getMissionsByStatus(MissionStatus status) {
        return missions.values().stream()
                .filter(m -> m.status() == status)
                .collect(Collectors.toList());
    }

    /**
     * Get the mission board (POSTED and ACTIVE missions).
     */
    public List<Mission> getMissionBoard() {
        return missions.values().stream()
                .filter(m -> m.status().canAccept())
                .sorted(Comparator.comparing(Mission::createdTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get missions a player is participating in.
     */
    public List<Mission> getPlayerActiveMissions(UUID playerId) {
        return missions.values().stream()
                .filter(m -> m.status() == MissionStatus.ACTIVE && m.hasParticipant(playerId))
                .collect(Collectors.toList());
    }

    /**
     * Get all missions a player has participated in.
     */
    public List<Mission> getPlayerMissionHistory(UUID playerId) {
        return missions.values().stream()
                .filter(m -> m.hasParticipant(playerId))
                .sorted(Comparator.comparing(Mission::createdTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get missions created by a player.
     */
    public List<Mission> getMissionsCreatedBy(UUID playerId) {
        return missions.values().stream()
                .filter(m -> playerId.equals(m.creatorId()))
                .sorted(Comparator.comparing(Mission::createdTime).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get all missions.
     */
    public Map<UUID, Mission> getAllMissions() {
        return Collections.unmodifiableMap(missions);
    }

    // ===== Tutorial Methods =====

    /**
     * Check if tutorial missions have been generated.
     */
    public boolean areTutorialsGenerated() {
        return tutorialsGenerated;
    }

    /**
     * Mark tutorial missions as generated.
     */
    public void setTutorialsGenerated(boolean generated) {
        this.tutorialsGenerated = generated;
        setDirty();
    }

    // ===== Record Types =====

    /**
     * Record tracking a player's Starfleet career.
     */
    public record PlayerStarfleetRecord(
            UUID playerId,
            String lastKnownName,
            long totalXp,
            List<UUID> completedMissions,
            long lastActivityTime
    ) {
        public PlayerStarfleetRecord withXp(long newXp) {
            return new PlayerStarfleetRecord(playerId, lastKnownName, newXp, completedMissions, System.currentTimeMillis());
        }

        public PlayerStarfleetRecord withName(String name) {
            return new PlayerStarfleetRecord(playerId, name, totalXp, completedMissions, lastActivityTime);
        }

        public PlayerStarfleetRecord withMission(UUID missionId) {
            List<UUID> newMissions = new ArrayList<>(completedMissions);
            if (!newMissions.contains(missionId)) {
                newMissions.add(missionId);
            }
            return new PlayerStarfleetRecord(playerId, lastKnownName, totalXp, newMissions, System.currentTimeMillis());
        }

        public StarfleetRank getRank() {
            return StarfleetRank.fromXp(totalXp);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("PlayerId", playerId);
            tag.putString("LastKnownName", lastKnownName);
            tag.putLong("TotalXp", totalXp);
            tag.putLong("LastActivityTime", lastActivityTime);

            ListTag missionList = new ListTag();
            for (UUID missionId : completedMissions) {
                CompoundTag mTag = new CompoundTag();
                mTag.putUUID("MissionId", missionId);
                missionList.add(mTag);
            }
            tag.put("CompletedMissions", missionList);

            return tag;
        }

        public static PlayerStarfleetRecord load(CompoundTag tag) {
            UUID playerId = tag.getUUID("PlayerId");
            String lastKnownName = tag.getString("LastKnownName");
            long totalXp = tag.getLong("TotalXp");
            long lastActivityTime = tag.getLong("LastActivityTime");

            List<UUID> missions = new ArrayList<>();
            if (tag.contains("CompletedMissions", Tag.TAG_LIST)) {
                ListTag missionList = tag.getList("CompletedMissions", Tag.TAG_COMPOUND);
                for (int i = 0; i < missionList.size(); i++) {
                    missions.add(missionList.getCompound(i).getUUID("MissionId"));
                }
            }

            return new PlayerStarfleetRecord(playerId, lastKnownName, totalXp, missions, lastActivityTime);
        }
    }
}
