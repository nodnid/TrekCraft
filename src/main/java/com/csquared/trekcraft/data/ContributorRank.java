package com.csquared.trekcraft.data;

public enum ContributorRank {
    CREWMAN(0, "Crewman", 0),
    ENSIGN(10, "Ensign", 1),
    LIEUTENANT(50, "Lieutenant", 2),
    LT_COMMANDER(100, "Lt. Commander", 3),
    COMMANDER(250, "Commander", 5),
    CAPTAIN(500, "Captain", 8),
    ADMIRAL(1000, "Admiral", 16);

    private final int threshold;
    private final String title;
    private final int diamondReward;

    ContributorRank(int threshold, String title, int diamondReward) {
        this.threshold = threshold;
        this.title = title;
        this.diamondReward = diamondReward;
    }

    public int getThreshold() {
        return threshold;
    }

    public String getTitle() {
        return title;
    }

    public int getDiamondReward() {
        return diamondReward;
    }

    /**
     * Get the rank for a given net contribution amount.
     * Returns the highest rank where contribution >= threshold.
     */
    public static ContributorRank forContribution(long contribution) {
        ContributorRank result = CREWMAN;
        for (ContributorRank rank : values()) {
            if (contribution >= rank.threshold) {
                result = rank;
            } else {
                break;
            }
        }
        return result;
    }

    /**
     * Get the next rank after this one, or null if this is the highest rank.
     */
    public ContributorRank getNextRank() {
        int nextOrdinal = this.ordinal() + 1;
        if (nextOrdinal < values().length) {
            return values()[nextOrdinal];
        }
        return null;
    }

    /**
     * Get progress towards next rank as a value between 0.0 and 1.0.
     */
    public static double getProgressToNextRank(long contribution) {
        ContributorRank current = forContribution(contribution);
        ContributorRank next = current.getNextRank();
        if (next == null) {
            return 1.0; // Already at max rank
        }
        long progress = contribution - current.threshold;
        long needed = next.threshold - current.threshold;
        return Math.min(1.0, (double) progress / needed);
    }
}
