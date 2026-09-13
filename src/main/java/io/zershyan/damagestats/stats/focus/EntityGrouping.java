package io.zershyan.damagestats.stats.focus;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** 图表中的实体聚合粒度。 */
public enum EntityGrouping {
    TYPE,
    INSTANCE;

    public static final StreamCodec<FriendlyByteBuf, EntityGrouping> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(EntityGrouping::fromOrdinal, EntityGrouping::ordinal);

    private static EntityGrouping fromOrdinal(int ordinal) {
        EntityGrouping[] values = values();
        if(ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("非法的实体聚合粒度");
        return values[ordinal];
    }
}
