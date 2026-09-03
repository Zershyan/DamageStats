package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

    /** 序列化用的扁平快照。顶层累加器和分组子累加器共用同一份字段清单，不必维护两套 */
    private record Totals(
            float totalOriginal, float totalActual, float totalBlocked,
            DamageReduction reduction,
            int hitCount, int killCount,
            float maxSingle, Optional<ResourceLocation> maxSingleDamageType, long maxSingleTime,
            float minSingle, long firstHitTime, long lastHitTime
    ) {
        static final Codec<Totals> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.fieldOf("original").forGetter(Totals::totalOriginal),
                Codec.FLOAT.fieldOf("actual").forGetter(Totals::totalActual),
                Codec.FLOAT.optionalFieldOf("blocked", 0f).forGetter(Totals::totalBlocked),
                DamageReduction.CODEC.optionalFieldOf("reduction", DamageReduction.NONE).forGetter(Totals::reduction),
                Codec.INT.fieldOf("hits").forGetter(Totals::hitCount),
                Codec.INT.optionalFieldOf("kills", 0).forGetter(Totals::killCount),
                Codec.FLOAT.fieldOf("maxHit").forGetter(Totals::maxSingle),
                ResourceLocation.CODEC.optionalFieldOf("maxHitType").forGetter(Totals::maxSingleDamageType),
                Codec.LONG.optionalFieldOf("maxHitAt", 0L).forGetter(Totals::maxSingleTime),
                Codec.FLOAT.optionalFieldOf("minHit", 0f).forGetter(Totals::minSingle),
                Codec.LONG.fieldOf("firstHitAt").forGetter(Totals::firstHitTime),
                Codec.LONG.fieldOf("lastHitAt").forGetter(Totals::lastHitTime)
        ).apply(instance, Totals::new));
    }

    /** 分组子累加器：字段和顶层一样，只是没有再往下的分组 */
    public static final Codec<DamageAccumulator> LEAF_CODEC = Totals.CODEC.xmap(
            totals -> restore(new DamageAccumulator(false), totals), DamageAccumulator::totals);

    /** 对手分组的 key 是个对象，当不了 JSON 的键，只能存成列表 */
    private record OpponentGroup(EntityRef opponent, DamageAccumulator stats) {
        static final Codec<OpponentGroup> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityRef.CODEC.fieldOf("opponent").forGetter(OpponentGroup::opponent),
                LEAF_CODEC.fieldOf("stats").forGetter(OpponentGroup::stats)
        ).apply(instance, OpponentGroup::new));
    }

    public static final Codec<DamageAccumulator> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Totals.CODEC.fieldOf("totals").forGetter(DamageAccumulator::totals),
            Codec.unboundedMap(ResourceLocation.CODEC, LEAF_CODEC)
                    .optionalFieldOf("byType", Map.of()).forGetter(acc -> acc.byDamageType),
            Codec.unboundedMap(ResourceLocation.CODEC, LEAF_CODEC)
                    .optionalFieldOf("bySource", Map.of()).forGetter(acc -> acc.byDirectSourceType),
            OpponentGroup.CODEC.listOf().optionalFieldOf("byOpponent", List.of())
                    .forGetter(DamageAccumulator::opponentGroups)
    ).apply(instance, DamageAccumulator::restoreGrouped));

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
    public int getHitCount() { return hitCount; }
    public int getKillCount() { return killCount; }
    public float getMaxSingle() { return maxSingle; }
    public @Nullable ResourceLocation getMaxSingleDamageType() { return maxSingleDamageType; }

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

    private Totals totals() {
        return new Totals(totalOriginal, totalActual, totalBlocked, totalReduction, hitCount, killCount,
                maxSingle, Optional.ofNullable(maxSingleDamageType), maxSingleTime,
                getMinSingle(), firstHitTime, lastHitTime);
    }

    private List<OpponentGroup> opponentGroups() {
        return byOpponent.entrySet().stream()
                .map(group -> new OpponentGroup(group.getKey(), group.getValue()))
                .toList();
    }

    private static DamageAccumulator restore(DamageAccumulator target, Totals totals) {
        target.totalOriginal = totals.totalOriginal();
        target.totalActual = totals.totalActual();
        target.totalBlocked = totals.totalBlocked();
        target.totalReduction = totals.reduction();
        target.hitCount = totals.hitCount();
        target.killCount = totals.killCount();
        target.maxSingle = totals.maxSingle();
        target.maxSingleDamageType = totals.maxSingleDamageType().orElse(null);
        target.maxSingleTime = totals.maxSingleTime();
        target.minSingle = totals.minSingle();
        target.firstHitTime = totals.firstHitTime();
        target.lastHitTime = totals.lastHitTime();
        return target;
    }

    private static DamageAccumulator restoreGrouped(Totals totals,
                                                    Map<ResourceLocation, DamageAccumulator> byType,
                                                    Map<ResourceLocation, DamageAccumulator> bySource,
                                                    List<OpponentGroup> byOpponent) {
        DamageAccumulator accumulator = restore(new DamageAccumulator(), totals);
        accumulator.byDamageType.putAll(byType);
        accumulator.byDirectSourceType.putAll(bySource);
        byOpponent.forEach(group -> accumulator.byOpponent.put(group.opponent(), group.stats()));
        return accumulator;
    }
}
