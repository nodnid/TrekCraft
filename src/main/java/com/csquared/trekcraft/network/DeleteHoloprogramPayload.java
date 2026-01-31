package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client->Server payload to delete a holoprogram.
 */
public record DeleteHoloprogramPayload(
        BlockPos controllerPos,
        String programName
) implements CustomPacketPayload {

    public static final Type<DeleteHoloprogramPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "delete_holoprogram")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteHoloprogramPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DeleteHoloprogramPayload::controllerPos,
            ByteBufCodecs.STRING_UTF8, DeleteHoloprogramPayload::programName,
            DeleteHoloprogramPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
