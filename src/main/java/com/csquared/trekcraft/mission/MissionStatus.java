package com.csquared.trekcraft.mission;

/**
 * Status of a mission in its lifecycle.
 */
public enum MissionStatus {
    /**
     * Mission is posted on the mission board, available for players to accept.
     */
    POSTED,

    /**
     * Mission has been accepted and is in progress.
     */
    ACTIVE,

    /**
     * Mission has been completed successfully.
     */
    COMPLETED,

    /**
     * Mission was cancelled by the creator or an admin.
     */
    CANCELLED;

    /**
     * Check if this status represents an active/ongoing mission.
     */
    public boolean isOngoing() {
        return this == POSTED || this == ACTIVE;
    }

    /**
     * Check if this status represents a finished mission.
     */
    public boolean isFinished() {
        return this == COMPLETED || this == CANCELLED;
    }

    /**
     * Check if players can join this mission.
     */
    public boolean canAccept() {
        return this == POSTED || this == ACTIVE;
    }
}
