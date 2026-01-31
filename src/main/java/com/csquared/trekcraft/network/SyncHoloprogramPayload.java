package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server->Client payload to sync a holoprogram schematic for local saving.
 */
public record SyncHoloprogramPayload(
        String programName,
        CompoundTag schematicData
) implements CustomPacketPayload {

    public static final Type<SyncHoloprogramPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "sync_holoprogram")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncHoloprogramPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SyncHoloprogramPayload::programName,
            ByteBufCodecs.COMPOUND_TAG, SyncHoloprogramPayload::schematicData,
            SyncHoloprogramPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
