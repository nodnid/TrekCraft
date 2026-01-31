package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client->Server payload to save the current holodeck contents as a holoprogram.
 */
public record SaveHoloprogramPayload(
        BlockPos controllerPos,
        String programName
) implements CustomPacketPayload {

    public static final Type<SaveHoloprogramPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "save_holoprogram")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveHoloprogramPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SaveHoloprogramPayload::controllerPos,
            ByteBufCodecs.STRING_UTF8, SaveHoloprogramPayload::programName,
            SaveHoloprogramPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
