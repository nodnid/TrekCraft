package com.csquared.trekcraft.network.mission;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client-to-server payload to accept a mission.
 */
public record AcceptMissionPayload(UUID missionId) implements CustomPacketPayload {

    public static final Type<AcceptMissionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "accept_mission"));

    public static final StreamCodec<FriendlyByteBuf, AcceptMissionPayload> STREAM_CODEC =
            StreamCodec.of(AcceptMissionPayload::encode, AcceptMissionPayload::decode);

    private static void encode(FriendlyByteBuf buf, AcceptMissionPayload payload) {
        buf.writeUUID(payload.missionId);
    }

    private static AcceptMissionPayload decode(FriendlyByteBuf buf) {
        return new AcceptMissionPayload(buf.readUUID());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
