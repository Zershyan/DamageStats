package io.zershyan.damagestats.stats.focus;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 图表中的实体聚合粒度。 */
public enum EntityGrouping {
    TYPE,
    INSTANCE;

    public static final StreamCodec<ByteBuf, EntityGrouping> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(EntityGrouping::fromOrdinal, EntityGrouping::ordinal);

    private static EntityGrouping fromOrdinal(int ordinal) {
        EntityGrouping[] values = values();
        if(ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("非法的实体聚合粒度");
        return values[ordinal];
    }
}
