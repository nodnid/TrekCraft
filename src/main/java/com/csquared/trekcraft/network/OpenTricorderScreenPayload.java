package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import com.csquared.trekcraft.data.TransporterNetworkSavedData.SignalType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record OpenTricorderScreenPayload(
        int fuel,
        int slips,
        boolean hasRoom,
        List<PadEntry> pads,
        List<SignalEntry> signals,
        boolean canCreateMissions
) implements CustomPacketPayload {

    public static final Type<OpenTricorderScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_tricorder_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTricorderScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, OpenTricorderScreenPayload::fuel,
            ByteBufCodecs.INT, OpenTricorderScreenPayload::slips,
            ByteBufCodecs.BOOL, OpenTricorderScreenPayload::hasRoom,
            PadEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenTricorderScreenPayload::pads,
            SignalEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), OpenTricorderScreenPayload::signals,
            ByteBufCodecs.BOOL, OpenTricorderScreenPayload::canCreateMissions,
            OpenTricorderScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Pad entry for transport selection
    public record PadEntry(String name, BlockPos pos) {
        public static final StreamCodec<RegistryFriendlyByteBuf, PadEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, PadEntry::name,
                BlockPos.STREAM_CODEC, PadEntry::pos,
                PadEntry::new
        );
    }

    // Signal entry for transport selection (includes type)
    public record SignalEntry(String name, UUID tricorderId, SignalType type) {
        public static final StreamCodec<RegistryFriendlyByteBuf, SignalEntry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, SignalEntry::name,
                ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), SignalEntry::tricorderId,
                ByteBufCodecs.STRING_UTF8.map(SignalType::valueOf, SignalType::name), SignalEntry::type,
                SignalEntry::new
        );
    }
}
