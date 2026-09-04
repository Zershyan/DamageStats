package io.zershyan.damagestats.stats.view;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 完整导出快照分块传输时的逻辑分区。 */
public enum ExportSection {
    SESSION_DAMAGE_TYPE,
    SESSION_DIRECT_SOURCE,
    SESSION_OPPONENT,
    LIFETIME_DAMAGE_TYPE,
    LIFETIME_DIRECT_SOURCE,
    LIFETIME_OPPONENT,
    HISTORY;

    public static final StreamCodec<ByteBuf, ExportSection> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ExportSection::fromOrdinal, ExportSection::ordinal);

    private static ExportSection fromOrdinal(int ordinal) {
        ExportSection[] values = values();
        if(ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("非法导出分区");
        return values[ordinal];
    }
}
