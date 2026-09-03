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
    /**
     * 对手分组的粒度。战斗会话用 {@code INSTANCE}——一场战斗对手数量有限，能精确到「那只 Boss」；
     * 永久累计用 {@code TYPE}——「这个存档对那只早就死了的僵尸打了多少」本来没意义，
     * 按实例存的话刷怪场几小时就能堆出上万个条目。{@code NONE} 是分组出来的子累加器。
     */
    public enum OpponentGrouping { INSTANCE, TYPE, NONE }

    public static final float TICKS_PER_SECOND = 20f;

    private final OpponentGrouping opponentGrouping;

    private float totalOriginal;
    private float totalActual;
    private float totalBlocked;
    private DamageReduction totalReduction = DamageReduction.NONE;
    private int hitCount;
    private int killCount;
    private float maxSingle;
    private @Nullable ResourceLocation maxSingleDamageType;
    private @Nullable EntityRef maxSingleDirectSource;
    private long maxSingleTime;
    private float minSingle;
    private long firstHitTime = -1;
    private long lastHitTime = -1;

    private final Map<ResourceLocation, DamageAccumulator> byDamageType;
    private final Map<ResourceLocation, DamageAccumulator> byDirectSourceType;
    private final Map<EntityRef, DamageAccumulator> byOpponent;

    public DamageAccumulator(OpponentGrouping opponentGrouping) {
        this.opponentGrouping = opponentGrouping;
        // 子累加器不再往下分组，给它建三个空 HashMap 纯属浪费——条目数量级上去之后这笔开销很可观
        boolean grouped = opponentGrouping != OpponentGrouping.NONE;
        this.byDamageType = grouped ? new HashMap<>() : Map.of();
        this.byDirectSourceType = grouped ? new HashMap<>() : Map.of();
        this.byOpponent = grouped ? new HashMap<>() : Map.of();
    }

    /** 序列化用的扁平快照。顶层累加器和分组子累加器共用同一份字段清单，不必维护两套 */
    private record Totals(
            float totalOriginal, float totalActual, float totalBlocked,
            DamageReduction reduction,
            int hitCount, int killCount,
            float maxSingle, Optional<ResourceLocation> maxSingleDamageType,
            Optional<EntityRef> maxSingleDirectSource, long maxSingleTime,
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
                EntityRef.CODEC.optionalFieldOf("maxHitDirectSource").forGetter(Totals::maxSingleDirectSource),
                Codec.LONG.optionalFieldOf("maxHitAt", 0L).forGetter(Totals::maxSingleTime),
                Codec.FLOAT.optionalFieldOf("minHit", 0f).forGetter(Totals::minSingle),
                Codec.LONG.fieldOf("firstHitAt").forGetter(Totals::firstHitTime),
                Codec.LONG.fieldOf("lastHitAt").forGetter(Totals::lastHitTime)
        ).apply(instance, Totals::new));
    }

    /** 分组子累加器：字段和顶层一样，只是没有再往下的分组 */
    public static final Codec<DamageAccumulator> LEAF_CODEC = Totals.CODEC.xmap(
            totals -> restore(new DamageAccumulator(OpponentGrouping.NONE), totals), DamageAccumulator::totals);

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
            maxSingleDirectSource = record.directSource();
            maxSingleTime = record.gameTime();
        }
        if(record.actualDamage() > 0 && (minSingle == 0 || record.actualDamage() < minSingle)) {
            minSingle = record.actualDamage();
        }
        if(firstHitTime < 0) firstHitTime = record.gameTime();
        lastHitTime = record.gameTime();

        if(opponentGrouping == OpponentGrouping.NONE) return;
        group(byDamageType, record.damageTypeId()).accept(record, opponent);
        group(byDirectSourceType, record.directSource().typeIdOrEnvironment()).accept(record, opponent);
        group(byOpponent, opponentKey(opponent)).accept(record, opponent);
    }

    /** 按这个累加器自己的粒度查对手分组，调用方不必关心它是会话级还是累计级 */
    public @Nullable DamageAccumulator opponentGroup(EntityRef opponent) {
        return byOpponent.get(opponentKey(opponent));
    }

    /** 环境来源本来就是个单例，不需要再折叠一次 */
    private EntityRef opponentKey(EntityRef opponent) {
        if(opponentGrouping != OpponentGrouping.TYPE || opponent.isEnvironment()) return opponent;
        return EntityRef.ofType(opponent.typeIdOrEnvironment());
    }

    private static <K> DamageAccumulator group(Map<K, DamageAccumulator> map, K key) {
        return map.computeIfAbsent(key, k -> new DamageAccumulator(OpponentGrouping.NONE));
    }

    public float getTotalActual() { return totalActual; }
    public float getTotalOriginal() { return totalOriginal; }
    public int getHitCount() { return hitCount; }
    public int getKillCount() { return killCount; }
    public float getMaxSingle() { return maxSingle; }
    public DamageReduction getTotalReduction() { return totalReduction; }
    public @Nullable ResourceLocation getMaxSingleDamageType() { return maxSingleDamageType; }
    public @Nullable EntityRef getMaxSingleDirectSource() { return maxSingleDirectSource; }
    public long getMaxSingleTime() { return maxSingleTime; }
    public float getTotalBlocked() { return totalBlocked; }
    public OpponentGrouping getOpponentGrouping() { return opponentGrouping; }

    /** 合并同一统计粒度的数据，供旧版存档升级和全局汇总恢复使用 */
    void absorb(DamageAccumulator other) {
        totalOriginal += other.totalOriginal;
        totalActual += other.totalActual;
        totalBlocked += other.totalBlocked;
        totalReduction = totalReduction.plus(other.totalReduction);
        hitCount += other.hitCount;
        killCount += other.killCount;
        if(other.maxSingle > maxSingle) {
            maxSingle = other.maxSingle;
            maxSingleDamageType = other.maxSingleDamageType;
            maxSingleDirectSource = other.maxSingleDirectSource;
            maxSingleTime = other.maxSingleTime;
        }
        if(other.minSingle > 0 && (minSingle == 0 || other.minSingle < minSingle)) {
            minSingle = other.minSingle;
        }
        if(other.firstHitTime >= 0 && (firstHitTime < 0 || other.firstHitTime < firstHitTime)) {
            firstHitTime = other.firstHitTime;
        }
        if(other.lastHitTime > lastHitTime) lastHitTime = other.lastHitTime;

        if(opponentGrouping == OpponentGrouping.NONE) return;
        other.byDamageType.forEach((key, value) ->
                byDamageType.computeIfAbsent(key, ignored -> new DamageAccumulator(OpponentGrouping.NONE)).absorb(value));
        other.byDirectSourceType.forEach((key, value) ->
                byDirectSourceType.computeIfAbsent(key, ignored -> new DamageAccumulator(OpponentGrouping.NONE)).absorb(value));
        other.byOpponent.forEach((key, value) ->
                byOpponent.computeIfAbsent(opponentKey(key), ignored -> new DamageAccumulator(OpponentGrouping.NONE))
                        .absorb(value));
    }

    public float getMinSingle() {
        return minSingle;
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

    /** 本场平均 DPS 按真实时长计算；同一游戏刻内没有正时长时，最小分母为一游戏刻 */
    public float getAverageDps() {
        return hitCount == 0 ? 0 : totalActual / effectiveSeconds();
    }

    private float effectiveSeconds() {
        return Math.max(1, getDurationTicks()) / TICKS_PER_SECOND;
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
                maxSingle, Optional.ofNullable(maxSingleDamageType), Optional.ofNullable(maxSingleDirectSource), maxSingleTime,
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
        target.maxSingleDirectSource = totals.maxSingleDirectSource().orElse(null);
        target.maxSingleTime = totals.maxSingleTime();
        target.minSingle = totals.minSingle();
        target.firstHitTime = totals.firstHitTime();
        target.lastHitTime = totals.lastHitTime();
        return target;
    }

    /** 只有永久累计会被持久化，所以恢复出来的一律是类型级粒度 */
    private static DamageAccumulator restoreGrouped(Totals totals,
                                                    Map<ResourceLocation, DamageAccumulator> byType,
                                                    Map<ResourceLocation, DamageAccumulator> bySource,
                                                    List<OpponentGroup> byOpponent) {
        DamageAccumulator accumulator = restore(new DamageAccumulator(OpponentGrouping.TYPE), totals);
        accumulator.byDamageType.putAll(byType);
        accumulator.byDirectSourceType.putAll(bySource);
        byOpponent.forEach(group -> accumulator.byOpponent
                .computeIfAbsent(accumulator.opponentKey(group.opponent()), ignored ->
                        new DamageAccumulator(OpponentGrouping.NONE))
                .absorb(group.stats()));
        return accumulator;
    }
}
