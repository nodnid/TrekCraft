package com.csquared.trekcraft.network.mission;

import com.csquared.trekcraft.TrekCraftMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-server payload for creating a new mission.
 *
 * @param title         Mission title (max 32 chars)
 * @param description   Mission description/briefing (max 128 chars)
 * @param objectiveType Objective type: "kill", "gather", "explore", "scan"
 * @param objectiveData Pipe-delimited objective parameters:
 *                      - kill: "entityType|count" e.g. "minecraft:zombie|20" or "|20" for any hostile
 *                      - gather: "itemId|quantity" e.g. "minecraft:raw_iron|32"
 *                      - explore: "count" e.g. "5"
 *                      - scan: "entityTypes|blockTypes|count" e.g. "minecraft:cow,minecraft:pig||10"
 * @param xpReward      XP reward for completion
 * @param minRank       Minimum rank name (e.g. "CREWMAN", "ENSIGN")
 */
public record CreateMissionPayload(
        String title,
        String description,
        String objectiveType,
        String objectiveData,
        int xpReward,
        String minRank
) implements CustomPacketPayload {

    public static final Type<CreateMissionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(TrekCraftMod.MODID, "create_mission"));

    public static final StreamCodec<FriendlyByteBuf, CreateMissionPayload> STREAM_CODEC =
            StreamCodec.of(CreateMissionPayload::encode, CreateMissionPayload::decode);

    private static void encode(FriendlyByteBuf buf, CreateMissionPayload payload) {
        buf.writeUtf(payload.title, 32);
        buf.writeUtf(payload.description, 128);
        buf.writeUtf(payload.objectiveType, 16);
        buf.writeUtf(payload.objectiveData, 256);
        buf.writeInt(payload.xpReward);
        buf.writeUtf(payload.minRank, 32);
    }

    private static CreateMissionPayload decode(FriendlyByteBuf buf) {
        String title = buf.readUtf(32);
        String description = buf.readUtf(128);
        String objectiveType = buf.readUtf(16);
        String objectiveData = buf.readUtf(256);
        int xpReward = buf.readInt();
        String minRank = buf.readUtf(32);
        return new CreateMissionPayload(title, description, objectiveType, objectiveData, xpReward, minRank);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
