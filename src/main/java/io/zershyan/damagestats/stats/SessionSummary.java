package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * 一场已结束战斗的摘要。已结束的会话只会被读这四个数字，
 * 没人会去查「第三场的伤害类型分解」，所以归档时把完整聚合器丢掉只留摘要——
 * 既省内存，也让持久化变得平凡。
 */
public record SessionSummary(float totalDamage, float averageDps, long durationTicks, int hitCount) {
    public static final Codec<SessionSummary> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("damage").forGetter(SessionSummary::totalDamage),
            Codec.FLOAT.fieldOf("dps").forGetter(SessionSummary::averageDps),
            Codec.LONG.fieldOf("ticks").forGetter(SessionSummary::durationTicks),
            Codec.INT.fieldOf("hits").forGetter(SessionSummary::hitCount)
    ).apply(instance, SessionSummary::new));

    public float durationSeconds() {
        return durationTicks / DamageAccumulator.TICKS_PER_SECOND;
    }
}
