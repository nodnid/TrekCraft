package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client-to-server payload to set a tricorder's name.
 */
public record SetTricorderNamePayload(
        UUID tricorderId,
        String name
) implements CustomPacketPayload {

    public static final Type<SetTricorderNamePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "set_tricorder_name")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetTricorderNamePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), SetTricorderNamePayload::tricorderId,
            ByteBufCodecs.STRING_UTF8, SetTricorderNamePayload::name,
            SetTricorderNamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
