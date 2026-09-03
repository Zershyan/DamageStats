package io.zershyan.damagestats.stats.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** GUI 打开时向服务端请求一次拿到的完整数据 */
public record StatsSnapshot(EntryView outgoing, EntryView incoming, List<SessionView> history) {
    public static final StreamCodec<RegistryFriendlyByteBuf, StatsSnapshot> STREAM_CODEC = StreamCodec.composite(
            EntryView.STREAM_CODEC, StatsSnapshot::outgoing,
            EntryView.STREAM_CODEC, StatsSnapshot::incoming,
            SessionView.LIST_STREAM_CODEC, StatsSnapshot::history,
            StatsSnapshot::new
    );
}
