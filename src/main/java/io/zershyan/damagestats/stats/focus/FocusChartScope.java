package io.zershyan.damagestats.stats.focus;

import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** 页间图表读取本场还是累计数据。 */
public enum FocusChartScope {
    SESSION,
    LIFETIME;

    public static final StreamCodec<FriendlyByteBuf, FocusChartScope> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBoolean(value == SESSION),
            buf -> buf.readBoolean() ? SESSION : LIFETIME
    );
}
