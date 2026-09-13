package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.ComponentSerialization;
import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.DamageAccumulator;
import io.zershyan.damagestats.stats.DamageReduction;
import io.zershyan.damagestats.stats.EntityRef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * 一份指标的传输视图。只带原始量，平均值、减伤率这类派生值用纯函数现算——既省网络字节，
 * 也保证两端算法一致。名称字段用 Component 而不是渲染好的字符串，
 * 于是服务端是英文、客户端是中文时玩家看到的仍然是中文。
 */
public record MetricsView(
        float totalActual,
        float totalOriginal,
        float totalBlocked,
        DamageReduction reduction,
        int hitCount,
        int killCount,
        float maxSingle,
        @Nullable ResourceLocation maxSingleDamageTypeId,
        Component maxSingleTypeName,
        @Nullable EntityRef maxSingleDirectSource,
        Component maxSingleDirectSourceName,
        long maxSingleTime,
        float minSingle,
        float averageDps,
        float realtimeDps,
        long durationTicks,
        float maxOriginal,
        float realtimeOriginalDps
) {
    /** 字段数超过 StreamCodec.composite 的六个上限，只能手写 */
    public static final StreamCodec<FriendlyByteBuf, MetricsView> STREAM_CODEC =
            StreamCodec.of(MetricsView::encode, MetricsView::decode);

    /** 没有活跃会话时用它占位，客户端看 hitCount 是否为 0 就知道有没有数据 */
    public static final MetricsView EMPTY = new MetricsView(
            0, 0, 0, DamageReduction.NONE, 0, 0, 0,
            null, Component.empty(), null, Component.empty(), 0, 0, 0, 0, 0, 0, 0);

    public static MetricsView of(DamageAccumulator acc,
                                 Component maxSingleTypeName,
                                 Component maxSingleDirectSourceName,
                                 float realtimeDps) {
        return of(acc, maxSingleTypeName, maxSingleDirectSourceName, realtimeDps, 0);
    }

    public static MetricsView of(DamageAccumulator acc,
                                 Component maxSingleTypeName,
                                 Component maxSingleDirectSourceName,
                                 float realtimeDps,
                                 float realtimeOriginalDps) {
        return new MetricsView(
                acc.getTotalActual(),
                acc.getTotalOriginal(),
                acc.getTotalBlocked(),
                acc.getTotalReduction(),
                acc.getHitCount(),
                acc.getKillCount(),
                acc.getMaxSingle(),
                acc.getMaxSingleDamageType(),
                maxSingleTypeName,
                acc.getMaxSingleDirectSource(),
                maxSingleDirectSourceName,
                acc.getMaxSingleTime(),
                acc.getMinSingle(),
                acc.getAverageDps(),
                realtimeDps,
                acc.getDurationTicks(),
                acc.getMaxOriginal(),
                realtimeOriginalDps);
    }

    public boolean isEmpty() {
        return hitCount == 0;
    }

    public float averageDamage() {
        return hitCount == 0 ? 0 : totalActual / hitCount;
    }

    public float averageOriginalDps() {
        float seconds = Math.max(1L, durationTicks) / DamageAccumulator.TICKS_PER_SECOND;
        return hitCount == 0 ? 0 : totalOriginal / seconds;
    }

    /** 取原始与实际的差值，而不是六项减免之和——别的 mod 可能直接改最终伤害而不走减免记账 */
    public float reducedDamage() {
        return Math.max(0, totalOriginal - totalActual);
    }

    public float reductionRate() {
        return totalOriginal <= 0 ? 0 : reducedDamage() / totalOriginal;
    }

    public float durationSeconds() {
        return durationTicks / DamageAccumulator.TICKS_PER_SECOND;
    }

    /** 同一游戏刻内没有正时长时，以一游戏刻为最小分母，理由同 {@link DamageAccumulator#getAverageDps()} */
    public float hitsPerSecond() {
        float seconds = Math.max(1L, durationTicks) / DamageAccumulator.TICKS_PER_SECOND;
        return hitCount == 0 ? 0 : hitCount / seconds;
    }

    private static void encode(FriendlyByteBuf buf, MetricsView view) {
        buf.writeFloat(view.totalActual);
        buf.writeFloat(view.totalOriginal);
        buf.writeFloat(view.totalBlocked);
        DamageReduction.STREAM_CODEC.encode(buf, view.reduction);
        buf.writeVarInt(view.hitCount);
        buf.writeVarInt(view.killCount);
        buf.writeFloat(view.maxSingle);
        ByteBufCodecs.optional(ByteBufCodecs.RESOURCE_LOCATION)
                .encode(buf, Optional.ofNullable(view.maxSingleDamageTypeId));
        ComponentSerialization.STREAM_CODEC.encode(buf, view.maxSingleTypeName);
        ByteBufCodecs.optional(EntityRef.STREAM_CODEC)
                .encode(buf, Optional.ofNullable(view.maxSingleDirectSource));
        ComponentSerialization.STREAM_CODEC.encode(buf, view.maxSingleDirectSourceName);
        buf.writeVarLong(view.maxSingleTime);
        buf.writeFloat(view.minSingle);
        buf.writeFloat(view.averageDps);
        buf.writeFloat(view.realtimeDps);
        buf.writeVarLong(view.durationTicks);
        buf.writeFloat(view.maxOriginal);
        buf.writeFloat(view.realtimeOriginalDps);
    }

    private static MetricsView decode(FriendlyByteBuf buf) {
        return new MetricsView(
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                DamageReduction.STREAM_CODEC.decode(buf),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readFloat(),
                ByteBufCodecs.optional(ByteBufCodecs.RESOURCE_LOCATION).decode(buf).orElse(null),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                ByteBufCodecs.optional(EntityRef.STREAM_CODEC).decode(buf).orElse(null),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                buf.readVarLong(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarLong(),
                buf.readFloat(),
                buf.readFloat());
    }
}
