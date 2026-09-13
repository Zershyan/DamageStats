package io.zershyan.damagestats.network;

import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

public final class ComponentSerialization {
    public static final StreamCodec<FriendlyByteBuf, Component> STREAM_CODEC = StreamCodec.of(
            FriendlyByteBuf::writeComponent,
            FriendlyByteBuf::readComponent
    );
}
