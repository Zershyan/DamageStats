package io.zershyan.damagestats.network.codec;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ByteBufCodecs {
    public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL = StreamCodec.of(
            FriendlyByteBuf::writeBoolean,
            FriendlyByteBuf::readBoolean);
    public static final StreamCodec<FriendlyByteBuf, Float> FLOAT = StreamCodec.of(
            FriendlyByteBuf::writeFloat,
            FriendlyByteBuf::readFloat);
    public static final StreamCodec<FriendlyByteBuf, Integer> VAR_INT = StreamCodec.of(
            FriendlyByteBuf::writeVarInt,
            FriendlyByteBuf::readVarInt);
    public static final StreamCodec<FriendlyByteBuf, Long> VAR_LONG = StreamCodec.of(
            FriendlyByteBuf::writeVarLong,
            FriendlyByteBuf::readVarLong);
    public static final StreamCodec<FriendlyByteBuf, String> STRING_UTF = StreamCodec.of(
            FriendlyByteBuf::writeUtf,
            FriendlyByteBuf::readUtf);
    public static final StreamCodec<FriendlyByteBuf, String> STRING_UTF8 = STRING_UTF;
    public static final StreamCodec<FriendlyByteBuf, UUID> UUID = StreamCodec.of(
            FriendlyByteBuf::writeUUID,
            FriendlyByteBuf::readUUID);
    public static final StreamCodec<FriendlyByteBuf, ResourceLocation> RESOURCE_LOCATION = StreamCodec.of(
            FriendlyByteBuf::writeResourceLocation,
            FriendlyByteBuf::readResourceLocation);

    public static <V> StreamCodec<FriendlyByteBuf, Optional<V>> optional(
            StreamCodec<FriendlyByteBuf, V> codec) {
        return StreamCodec.of(
                (buffer, value) -> {
                    buffer.writeBoolean(value.isPresent());
                    value.ifPresent(element -> codec.encode(buffer, element));
                },
                buffer -> buffer.readBoolean() ? Optional.of(codec.decode(buffer)) : Optional.empty());
    }

    public static <V> StreamCodec.CodecOperation<FriendlyByteBuf, V, List<V>> list() {
        return list(Integer.MAX_VALUE);
    }

    public static <V> StreamCodec.CodecOperation<FriendlyByteBuf, V, List<V>> list(int maxSize) {
        if(maxSize < 0) throw new IllegalArgumentException("maxSize must not be negative");
        return codec -> StreamCodec.of(
                (buffer, values) -> {
                    if(values.size() > maxSize) {
                        throw new IllegalArgumentException("List is too large: " + values.size() + " > " + maxSize);
                    }
                    buffer.writeVarInt(values.size());
                    for(V value : values) codec.encode(buffer, value);
                },
                buffer -> {
                    int size = buffer.readVarInt();
                    if(size < 0 || size > maxSize) {
                        throw new IllegalArgumentException("Invalid list size: " + size + " > " + maxSize);
                    }
                    List<V> values = new ArrayList<>(size);
                    for(int i = 0; i < size; i++) values.add(codec.decode(buffer));
                    return List.copyOf(values);
                });
    }
}
