package io.zershyan.damagestats.stats.focus;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** 页间横向直方图的纵轴维度。 */
public enum FocusChartDimension {
    DAMAGE_TYPE,
    DIRECT_SOURCE,
    RESPONSIBLE_SOURCE,
    TARGET;

    public static final StreamCodec<FriendlyByteBuf, FocusChartDimension> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(FocusChartDimension::valueOf, FocusChartDimension::ordinal);

    private static FocusChartDimension valueOf(int ordinal) {
        FocusChartDimension[] values = values();
        if(ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("非法焦点图表维度");
        return values[ordinal];
    }
}
