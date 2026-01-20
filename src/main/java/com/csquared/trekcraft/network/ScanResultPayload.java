package com.csquared.trekcraft.network;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Payload sent from server to client containing scan results for isometric 3D visualization.
 */
public record ScanResultPayload(
        String facing,
        List<ScannedBlock> blocks,
        List<ScannedEntity> entities
) implements CustomPacketPayload {

    public static final Type<ScanResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "scan_result")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ScanResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ScanResultPayload::facing,
            ScannedBlock.STREAM_CODEC.apply(ByteBufCodecs.list()), ScanResultPayload::blocks,
            ScannedEntity.STREAM_CODEC.apply(ByteBufCodecs.list()), ScanResultPayload::entities,
            ScanResultPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Represents a single interesting block found during scan.
     * Coordinates are relative to scan origin (0-9 in each dimension).
     */
    public record ScannedBlock(int x, int y, int z, String blockId) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ScannedBlock> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, ScannedBlock::x,
                ByteBufCodecs.INT, ScannedBlock::y,
                ByteBufCodecs.INT, ScannedBlock::z,
                ByteBufCodecs.STRING_UTF8, ScannedBlock::blockId,
                ScannedBlock::new
        );
    }

    /**
     * Represents an entity found during scan.
     * Coordinates are relative positions within scan area (0.0-10.0 range).
     */
    public record ScannedEntity(float x, float y, float z, String entityType, float yaw) {
        public static final StreamCodec<RegistryFriendlyByteBuf, ScannedEntity> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, ScannedEntity::x,
                ByteBufCodecs.FLOAT, ScannedEntity::y,
                ByteBufCodecs.FLOAT, ScannedEntity::z,
                ByteBufCodecs.STRING_UTF8, ScannedEntity::entityType,
                ByteBufCodecs.FLOAT, ScannedEntity::yaw,
                ScannedEntity::new
        );
    }
}
