package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-client payload to open the wormhole linking screen.
 */
public record OpenWormholeLinkScreenPayload(
        String sourcePortalId,
        String sourcePortalName,
        List<PortalEntry> availablePortals
) implements CustomPacketPayload {

    public static final Type<OpenWormholeLinkScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_wormhole_link_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenWormholeLinkScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenWormholeLinkScreenPayload::sourcePortalId,
            ByteBufCodecs.STRING_UTF8, OpenWormholeLinkScreenPayload::sourcePortalName,
            PortalEntry.LIST_STREAM_CODEC, OpenWormholeLinkScreenPayload::availablePortals,
            OpenWormholeLinkScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public UUID getSourcePortalId() {
        return UUID.fromString(sourcePortalId);
    }

    /**
     * Represents a portal entry in the link screen.
     */
    public record PortalEntry(
            String portalId,
            String name,
            int x, int y, int z,
            String dimensionKey
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PortalEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PortalEntry::portalId,
                ByteBufCodecs.STRING_UTF8, PortalEntry::name,
                ByteBufCodecs.VAR_INT, PortalEntry::x,
                ByteBufCodecs.VAR_INT, PortalEntry::y,
                ByteBufCodecs.VAR_INT, PortalEntry::z,
                ByteBufCodecs.STRING_UTF8, PortalEntry::dimensionKey,
                PortalEntry::new
        );

        public static final StreamCodec<RegistryFriendlyByteBuf, List<PortalEntry>> LIST_STREAM_CODEC =
                STREAM_CODEC.apply(ByteBufCodecs.list());

        public UUID getPortalId() {
            return UUID.fromString(portalId);
        }

        public String getPositionString() {
            return x + ", " + y + ", " + z;
        }

        /**
         * Get a short display name for the dimension.
         */
        public String getDimensionDisplayName() {
            // Convert dimension key to a user-friendly name
            return switch (dimensionKey) {
                case "minecraft:overworld" -> "Overworld";
                case "minecraft:the_nether" -> "Nether";
                case "minecraft:the_end" -> "The End";
                default -> {
                    // For modded dimensions, use the path after the colon
                    int colonIndex = dimensionKey.indexOf(':');
                    yield colonIndex >= 0 ? dimensionKey.substring(colonIndex + 1) : dimensionKey;
                }
            };
        }
    }
}
