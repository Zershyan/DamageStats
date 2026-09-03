package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.DamageAccumulator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一份指标的传输视图。只带原始量，平均值、减伤率这类派生值用纯函数现算——既省网络字节，
 * 也保证两端算法一致。名称字段用 Component 而不是渲染好的字符串，
 * 于是服务端是英文、客户端是中文时玩家看到的仍然是中文。
 */
public record MetricsView(
        float totalActual,
        float totalOriginal,
        int hitCount,
        int killCount,
        float maxSingle,
        Component maxSingleTypeName,
        float minSingle,
        float averageDps,
        float realtimeDps,
        long durationTicks
) {
    /** 字段数超过 StreamCodec.composite 的六个上限，只能手写 */
    public static final StreamCodec<RegistryFriendlyByteBuf, MetricsView> STREAM_CODEC =
            StreamCodec.of(MetricsView::encode, MetricsView::decode);

    /** 没有活跃会话时用它占位，客户端看 hitCount 是否为 0 就知道有没有数据 */
    public static final MetricsView EMPTY =
            new MetricsView(0, 0, 0, 0, 0, Component.empty(), 0, 0, 0, 0);

    public static MetricsView of(DamageAccumulator acc, Component maxSingleTypeName, float realtimeDps) {
        return new MetricsView(
                acc.getTotalActual(),
                acc.getTotalOriginal(),
                acc.getHitCount(),
                acc.getKillCount(),
                acc.getMaxSingle(),
                maxSingleTypeName,
                acc.getMinSingle(),
                acc.getAverageDps(),
                realtimeDps,
                acc.getDurationTicks());
    }

    public boolean isEmpty() {
        return hitCount == 0;
    }

    public float averageDamage() {
        return hitCount == 0 ? 0 : totalActual / hitCount;
    }

    public float reducedDamage() {
        return Math.max(0, totalOriginal - totalActual);
    }

    public float reductionRate() {
        return totalOriginal <= 0 ? 0 : reducedDamage() / totalOriginal;
    }

    public float durationSeconds() {
        return durationTicks / DamageAccumulator.TICKS_PER_SECOND;
    }

    private static void encode(RegistryFriendlyByteBuf buf, MetricsView view) {
        buf.writeFloat(view.totalActual);
        buf.writeFloat(view.totalOriginal);
        buf.writeVarInt(view.hitCount);
        buf.writeVarInt(view.killCount);
        buf.writeFloat(view.maxSingle);
        ComponentSerialization.STREAM_CODEC.encode(buf, view.maxSingleTypeName);
        buf.writeFloat(view.minSingle);
        buf.writeFloat(view.averageDps);
        buf.writeFloat(view.realtimeDps);
        buf.writeVarLong(view.durationTicks);
    }

    private static MetricsView decode(RegistryFriendlyByteBuf buf) {
        return new MetricsView(
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarLong());
    }
}
