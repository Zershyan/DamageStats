package io.zershyan.damagestats.stats.view;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 实例候选列表的服务端排序方式。 */
public enum EntityChoiceSort {
    RECENT,
    DAMAGE,
    HITS;

    public static final StreamCodec<ByteBuf, EntityChoiceSort> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(EntityChoiceSort::fromOrdinal, EntityChoiceSort::ordinal);

    private static EntityChoiceSort fromOrdinal(int ordinal) {
        EntityChoiceSort[] values = values();
        if(ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("非法实例排序方式");
        return values[ordinal];
    }

    public EntityChoiceSort next() {
        EntityChoiceSort[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
