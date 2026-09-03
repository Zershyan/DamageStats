package io.zershyan.damagestats.stats.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

/** 一个统计对象在某个方向上的完整数据 */
public record EntryView(Component ownerName, StatsView session, StatsView lifetime) {
    public static final StreamCodec<RegistryFriendlyByteBuf, EntryView> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, EntryView::ownerName,
            StatsView.STREAM_CODEC, EntryView::session,
            StatsView.STREAM_CODEC, EntryView::lifetime,
            EntryView::new
    );

    public static EntryView empty(Component ownerName) {
        return new EntryView(ownerName, StatsView.EMPTY, StatsView.EMPTY);
    }

    public boolean isEmpty() {
        return session.isEmpty() && lifetime.isEmpty();
    }
}
