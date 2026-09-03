package io.zershyan.damagestats.stats;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 伤害聚合器。累加原始与实际伤害，并同时按伤害类型、直接来源类型、对手三个维度分组。
 * 分组出来的子累加器不再往下分组，否则维度会指数展开。
 */
public class DamageAccumulator {
    public static final float TICKS_PER_SECOND = 20f;

    private final boolean grouped;

    private float totalOriginal;
    private float totalActual;
    private float totalBlocked;
    private DamageReduction totalReduction = DamageReduction.NONE;
    private int hitCount;
    private int killCount;
    private float maxSingle;
    private @Nullable ResourceLocation maxSingleDamageType;
    private long maxSingleTime;
    private float minSingle;
    private long firstHitTime = -1;
    private long lastHitTime = -1;

    private final Map<ResourceLocation, DamageAccumulator> byDamageType;
    private final Map<ResourceLocation, DamageAccumulator> byDirectSourceType;
    private final Map<EntityRef, DamageAccumulator> byOpponent;

    public DamageAccumulator() {
        this(true);
    }

    private DamageAccumulator(boolean grouped) {
        this.grouped = grouped;
        // 子累加器不再往下分组，给它建三个空 HashMap 纯属浪费——条目数量级上去之后这笔开销很可观
        this.byDamageType = grouped ? new HashMap<>() : Map.of();
        this.byDirectSourceType = grouped ? new HashMap<>() : Map.of();
        this.byOpponent = grouped ? new HashMap<>() : Map.of();
    }

    public void accept(DamageRecord record, EntityRef opponent) {
        totalOriginal += record.originalDamage();
        totalActual += record.actualDamage();
        totalBlocked += record.blockedDamage();
        totalReduction = totalReduction.plus(record.reduction());
        hitCount++;
        if(record.lethal()) killCount++;
        if(record.actualDamage() > maxSingle) {
            maxSingle = record.actualDamage();
            maxSingleDamageType = record.damageTypeId();
            maxSingleTime = record.gameTime();
        }
        if(hitCount == 1 || record.actualDamage() < minSingle) minSingle = record.actualDamage();
        if(firstHitTime < 0) firstHitTime = record.gameTime();
        lastHitTime = record.gameTime();

        if(!grouped) return;
        group(byDamageType, record.damageTypeId()).accept(record, opponent);
        group(byDirectSourceType, record.directSource().typeIdOrEnvironment()).accept(record, opponent);
        group(byOpponent, opponent).accept(record, opponent);
    }

    private static <K> DamageAccumulator group(Map<K, DamageAccumulator> map, K key) {
        return map.computeIfAbsent(key, k -> new DamageAccumulator(false));
    }

    public float getTotalActual() { return totalActual; }
    public float getTotalOriginal() { return totalOriginal; }
    public float getTotalBlocked() { return totalBlocked; }
    public DamageReduction getTotalReduction() { return totalReduction; }
    public int getHitCount() { return hitCount; }
    public int getKillCount() { return killCount; }
    public float getMaxSingle() { return maxSingle; }
    public @Nullable ResourceLocation getMaxSingleDamageType() { return maxSingleDamageType; }
    public long getMaxSingleTime() { return maxSingleTime; }
    public long getFirstHitTime() { return firstHitTime; }
    public long getLastHitTime() { return lastHitTime; }

    public float getMinSingle() {
        return hitCount == 0 ? 0 : minSingle;
    }

    public float getAverageDamage() {
        return hitCount == 0 ? 0 : totalActual / hitCount;
    }

    /** 被各环节吃掉的量。取差值而非减免记账之和，理由见 {@link DamageRecord#reducedDamage()} */
    public float getReducedDamage() {
        return Math.max(0, totalOriginal - totalActual);
    }

    public float getReductionRate() {
        return totalOriginal <= 0 ? 0 : getReducedDamage() / totalOriginal;
    }

    public long getDurationTicks() {
        return firstHitTime < 0 ? 0 : lastHitTime - firstHitTime;
    }

    /**
     * 本场平均 DPS。时长不足一秒时按一秒算：单次命中的真实时长是 0，
     * 直接相除会得到无穷大，按一秒算出来的数字更接近玩家的直觉。
     */
    public float getAverageDps() {
        return hitCount == 0 ? 0 : totalActual / effectiveSeconds();
    }

    public float getHitsPerSecond() {
        return hitCount == 0 ? 0 : hitCount / effectiveSeconds();
    }

    private float effectiveSeconds() {
        return Math.max(TICKS_PER_SECOND, getDurationTicks()) / TICKS_PER_SECOND;
    }

    public Map<ResourceLocation, DamageAccumulator> getByDamageType() {
        return Collections.unmodifiableMap(byDamageType);
    }

    public Map<ResourceLocation, DamageAccumulator> getByDirectSourceType() {
        return Collections.unmodifiableMap(byDirectSourceType);
    }

    public Map<EntityRef, DamageAccumulator> getByOpponent() {
        return Collections.unmodifiableMap(byOpponent);
    }
}
