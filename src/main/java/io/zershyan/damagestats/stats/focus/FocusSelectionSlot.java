package io.zershyan.damagestats.stats.focus;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 焦点选择器操作的槽位。 */
public enum FocusSelectionSlot {
    SOURCE,
    TARGET,
    DIRECT_SOURCE;

    public static final StreamCodec<ByteBuf, FocusSelectionSlot> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(FocusSelectionSlot::fromOrdinal, FocusSelectionSlot::ordinal);

    private static FocusSelectionSlot fromOrdinal(int ordinal) {
        FocusSelectionSlot[] values = values();
        if(ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("非法焦点选择槽位");
        return values[ordinal];
    }
}
