package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server->Client payload to open the holodeck controller screen.
 */
public record OpenHolodeckScreenPayload(
        BlockPos controllerPos,
        List<String> holoprograms
) implements CustomPacketPayload {

    public static final Type<OpenHolodeckScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_holodeck_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenHolodeckScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenHolodeckScreenPayload::controllerPos,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenHolodeckScreenPayload::holoprograms,
            OpenHolodeckScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
