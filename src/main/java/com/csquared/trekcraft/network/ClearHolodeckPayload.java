package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client->Server payload to clear the holodeck interior.
 */
public record ClearHolodeckPayload(
        BlockPos controllerPos
) implements CustomPacketPayload {

    public static final Type<ClearHolodeckPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "clear_holodeck")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ClearHolodeckPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ClearHolodeckPayload::controllerPos,
            ClearHolodeckPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
