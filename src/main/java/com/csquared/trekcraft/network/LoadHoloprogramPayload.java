package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client->Server payload to load a holoprogram into the holodeck.
 */
public record LoadHoloprogramPayload(
        BlockPos controllerPos,
        String programName
) implements CustomPacketPayload {

    public static final Type<LoadHoloprogramPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "load_holoprogram")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, LoadHoloprogramPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LoadHoloprogramPayload::controllerPos,
            ByteBufCodecs.STRING_UTF8, LoadHoloprogramPayload::programName,
            LoadHoloprogramPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
