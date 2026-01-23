package com.csquared.trekcraft.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Represents a wormhole portal in the network.
 */
public record WormholeRecord(
        UUID portalId,
        String name,
        BlockPos anchorPos,
        Direction.Axis axis,
        int width,
        int height,
        @Nullable UUID linkedPortalId,
        long createdTime,
        String dimensionKey
) {
    /**
     * Create a new unlinked wormhole record.
     */
    public static WormholeRecord create(UUID portalId, String name, BlockPos anchorPos,
                                         Direction.Axis axis, int width, int height, String dimensionKey) {
        return new WormholeRecord(portalId, name, anchorPos, axis, width, height, null, System.currentTimeMillis(), dimensionKey);
    }

    /**
     * Returns a new record with the specified linked portal.
     */
    public WormholeRecord withLink(UUID linkedId) {
        return new WormholeRecord(portalId, name, anchorPos, axis, width, height, linkedId, createdTime, dimensionKey);
    }

    /**
     * Returns a new record with no link.
     */
    public WormholeRecord withoutLink() {
        return new WormholeRecord(portalId, name, anchorPos, axis, width, height, null, createdTime, dimensionKey);
    }

    /**
     * Returns a new record with the specified name.
     */
    public WormholeRecord withName(String newName) {
        return new WormholeRecord(portalId, newName, anchorPos, axis, width, height, linkedPortalId, createdTime, dimensionKey);
    }

    /**
     * Check if this portal is linked to another.
     */
    public boolean isLinked() {
        return linkedPortalId != null;
    }
}
