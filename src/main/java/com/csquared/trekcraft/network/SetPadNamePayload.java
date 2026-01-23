package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload to set a transporter pad's name.
 */
public record SetPadNamePayload(
        BlockPos padPos,
        String name
) implements CustomPacketPayload {

    public static final Type<SetPadNamePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "set_pad_name")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SetPadNamePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SetPadNamePayload::padPos,
            ByteBufCodecs.STRING_UTF8, SetPadNamePayload::name,
            SetPadNamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
