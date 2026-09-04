package io.zershyan.damagestats.stats.focus;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** 页间图表读取本场还是累计数据。 */
public enum FocusChartScope {
    SESSION,
    LIFETIME;

    public static final StreamCodec<ByteBuf, FocusChartScope> STREAM_CODEC = StreamCodec.of(
            (buf, value) -> buf.writeBoolean(value == SESSION),
            buf -> buf.readBoolean() ? SESSION : LIFETIME
    );
}
