package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server-to-client payload to trigger the naming screen for tricorders or pads.
 */
public record OpenNamingScreenPayload(
        String namingType,
        String defaultName,
        String tricorderIdStr,  // Empty string if not applicable
        long padPosLong         // 0 if not applicable (use BlockPos.of/asLong)
) implements CustomPacketPayload {

    public static final Type<OpenNamingScreenPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "open_naming_screen")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenNamingScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenNamingScreenPayload::namingType,
            ByteBufCodecs.STRING_UTF8, OpenNamingScreenPayload::defaultName,
            ByteBufCodecs.STRING_UTF8, OpenNamingScreenPayload::tricorderIdStr,
            ByteBufCodecs.VAR_LONG, OpenNamingScreenPayload::padPosLong,
            OpenNamingScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum NamingType {
        TRICORDER,
        PAD,
        WORMHOLE
    }

    /**
     * Get the naming type enum value.
     */
    public NamingType getNamingType() {
        return NamingType.valueOf(namingType);
    }

    /**
     * Get the tricorder UUID if this is a tricorder naming request.
     */
    public UUID getTricorderId() {
        if (tricorderIdStr.isEmpty()) return null;
        return UUID.fromString(tricorderIdStr);
    }

    /**
     * Get the pad position if this is a pad naming request.
     */
    public BlockPos getPadPos() {
        if (padPosLong == 0) return null;
        return BlockPos.of(padPosLong);
    }

    /**
     * Factory method for tricorder naming screen.
     */
    public static OpenNamingScreenPayload forTricorder(UUID tricorderId, String defaultName) {
        return new OpenNamingScreenPayload(
                NamingType.TRICORDER.name(),
                defaultName,
                tricorderId.toString(),
                0L
        );
    }

    /**
     * Factory method for pad naming screen.
     */
    public static OpenNamingScreenPayload forPad(BlockPos padPos, String defaultName) {
        return new OpenNamingScreenPayload(
                NamingType.PAD.name(),
                defaultName,
                "",
                padPos.asLong()
        );
    }

    /**
     * Factory method for wormhole naming screen.
     */
    public static OpenNamingScreenPayload forWormhole(UUID portalId, String defaultName) {
        return new OpenNamingScreenPayload(
                NamingType.WORMHOLE.name(),
                defaultName,
                portalId.toString(),
                0L
        );
    }

    /**
     * Get the wormhole portal UUID if this is a wormhole naming request.
     */
    public UUID getWormholeId() {
        if (tricorderIdStr.isEmpty()) return null;
        return UUID.fromString(tricorderIdStr);
    }
}
