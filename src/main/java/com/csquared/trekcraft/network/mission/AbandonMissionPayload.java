package com.csquared.trekcraft.network.mission;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client-to-server payload to abandon a mission.
 */
public record AbandonMissionPayload(UUID missionId) implements CustomPacketPayload {

    public static final Type<AbandonMissionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "abandon_mission"));

    public static final StreamCodec<FriendlyByteBuf, AbandonMissionPayload> STREAM_CODEC =
            StreamCodec.of(AbandonMissionPayload::encode, AbandonMissionPayload::decode);

    private static void encode(FriendlyByteBuf buf, AbandonMissionPayload payload) {
        buf.writeUUID(payload.missionId);
    }

    private static AbandonMissionPayload decode(FriendlyByteBuf buf) {
        return new AbandonMissionPayload(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
