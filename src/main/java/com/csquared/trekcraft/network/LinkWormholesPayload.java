package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client-to-server payload to link two wormhole portals.
 */
public record LinkWormholesPayload(
        String portal1Id,
        String portal2Id
) implements CustomPacketPayload {

    public static final Type<LinkWormholesPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "link_wormholes")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LinkWormholesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, LinkWormholesPayload::portal1Id,
            ByteBufCodecs.STRING_UTF8, LinkWormholesPayload::portal2Id,
            LinkWormholesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public UUID getPortal1Id() {
        return UUID.fromString(portal1Id);
    }

    public UUID getPortal2Id() {
        return UUID.fromString(portal2Id);
    }
}
