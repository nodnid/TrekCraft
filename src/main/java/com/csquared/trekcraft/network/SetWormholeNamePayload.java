package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client-to-server payload to set a wormhole portal's name.
 */
public record SetWormholeNamePayload(
        UUID portalId,
        String name
) implements CustomPacketPayload {

    public static final Type<SetWormholeNamePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "set_wormhole_name")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetWormholeNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, payload -> payload.portalId().toString(),
            ByteBufCodecs.STRING_UTF8, SetWormholeNamePayload::name,
            (idStr, name) -> new SetWormholeNamePayload(UUID.fromString(idStr), name)
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
