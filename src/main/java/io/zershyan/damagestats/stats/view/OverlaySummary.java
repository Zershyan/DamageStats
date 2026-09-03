package io.zershyan.damagestats.stats.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Overlay 用的精简视图。每秒推一次，所以字段刻意压到最少：
 * 目标名、总伤害、DPS、命中次数正好对应 R5.3 的四项默认显示内容。
 */
public record OverlaySummary(
        Component targetName,
        float totalDamage,
        float averageDps,
        float realtimeDps,
        int hitCount,
        boolean active
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, OverlaySummary> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, OverlaySummary::targetName,
            ByteBufCodecs.FLOAT, OverlaySummary::totalDamage,
            ByteBufCodecs.FLOAT, OverlaySummary::averageDps,
            ByteBufCodecs.FLOAT, OverlaySummary::realtimeDps,
            ByteBufCodecs.VAR_INT, OverlaySummary::hitCount,
            ByteBufCodecs.BOOL, OverlaySummary::active,
            OverlaySummary::new
    );

    public static OverlaySummary empty() {
        return new OverlaySummary(Component.empty(), 0, 0, 0, 0, false);
    }
}
