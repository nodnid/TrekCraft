package com.csquared.trekcraft.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;
import java.util.UUID;

public record TricorderData(UUID tricorderId, Optional<String> label) {

    public static final Codec<TricorderData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUIDUtil.CODEC.fieldOf("tricorder_id").forGetter(TricorderData::tricorderId),
                    Codec.STRING.optionalFieldOf("label").forGetter(TricorderData::label)
            ).apply(instance, TricorderData::new)
    );

    public static final StreamCodec<ByteBuf, TricorderData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TricorderData::tricorderId,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), TricorderData::label,
            TricorderData::new
    );

    public static TricorderData create() {
        return new TricorderData(UUID.randomUUID(), Optional.empty());
    }

    public static TricorderData create(String label) {
        return new TricorderData(UUID.randomUUID(), Optional.of(label));
    }

    public TricorderData withLabel(String newLabel) {
        return new TricorderData(this.tricorderId, Optional.of(newLabel));
    }

    public String getDisplayName() {
        return label.orElse("Tricorder-" + tricorderId.toString().substring(0, 4).toUpperCase());
    }
}
