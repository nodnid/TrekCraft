package com.csquared.trekcraft.starfleet;

/**
 * Starfleet ranks earned through mission XP.
 * Ranks determine which missions a player can create and manage.
 */
public enum StarfleetRank {
    CREWMAN(0, "Crewman"),
    ENSIGN(100, "Ensign"),
    LIEUTENANT(500, "Lieutenant"),
    COMMANDER(2000, "Commander"),
    CAPTAIN(5000, "Captain"),
    ADMIRAL(-1, "Admiral");  // Server op only, not earned through XP

    private final int xpThreshold;
    private final String title;

    StarfleetRank(int xpThreshold, String title) {
        this.xpThreshold = xpThreshold;
        this.title = title;
    }

    public int getXpThreshold() {
        return xpThreshold;
    }

    public String getTitle() {
        return title;
    }

    /**
     * Get the rank for a given XP amount.
     * Returns the highest rank where xp >= threshold.
     * Note: ADMIRAL cannot be earned through XP, it requires op status.
     */
    public static StarfleetRank fromXp(long xp) {
        StarfleetRank result = CREWMAN;
        for (StarfleetRank rank : values()) {
            // Skip Admiral - it's not earned through XP
            if (rank == ADMIRAL) continue;

            if (xp >= rank.xpThreshold) {
                result = rank;
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * Get the next rank after this one, or null if this is the highest earnable rank.
     * Note: CAPTAIN is the highest earnable rank; ADMIRAL requires op status.
     */
    public StarfleetRank getNextRank() {
        // Admiral has no next rank
        if (this == ADMIRAL) return null;

        // Captain's next earnable rank is null (Admiral is op-only)
        if (this == CAPTAIN) return null;

        int nextOrdinal = this.ordinal() + 1;
        StarfleetRank next = values()[nextOrdinal];

        // Skip Admiral since it can't be earned through XP
        if (next == ADMIRAL) return null;

        return next;
    }

    /**
     * Get progress towards next rank as a value between 0.0 and 1.0.
     */
    public static double getProgressToNextRank(long xp) {
        StarfleetRank current = fromXp(xp);
        StarfleetRank next = current.getNextRank();

        if (next == null) {
            return 1.0; // Already at max earnable rank
        }

        long progress = xp - current.xpThreshold;
        long needed = next.xpThreshold - current.xpThreshold;
        return Math.min(1.0, (double) progress / needed);
    }

    /**
     * Get the XP needed to reach the next rank.
     */
    public static long getXpToNextRank(long currentXp) {
        StarfleetRank current = fromXp(currentXp);
        StarfleetRank next = current.getNextRank();

        if (next == null) {
            return 0; // Already at max earnable rank
        }

        return next.xpThreshold - currentXp;
    }

    /**
     * Check if this rank can create missions.
     * Lieutenant and above can create missions.
     */
    public boolean canCreateMissions() {
        return this.ordinal() >= LIEUTENANT.ordinal();
    }

    /**
     * Check if this rank can manage all missions (edit, cancel any mission).
     * Only Admiral can manage all missions.
     */
    public boolean canManageAllMissions() {
        return this == ADMIRAL;
    }

    /**
     * Check if this rank can accept missions of the given minimum rank.
     */
    public boolean canAcceptMission(StarfleetRank missionMinRank) {
        // Admiral can always accept
        if (this == ADMIRAL) return true;
        // Check if player rank >= mission minimum rank
        return this.ordinal() >= missionMinRank.ordinal();
    }
}
