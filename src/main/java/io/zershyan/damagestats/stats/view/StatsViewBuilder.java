package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.*;
import io.zershyan.damagestats.stats.filter.*;
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/** 服务端侧把聚合结果转成可传输的视图。名字在这一步就解析好，客户端不必再查注册表 */
public final class StatsViewBuilder {
    public static StatsSnapshot snapshotFor(DamageTracker tracker, ServerPlayer player, StatsFilter filter,
                                            StatsSubjectSlot subjectSlot, DamageTypeGrouping typeGrouping) {
        Component subject = subjectName(tracker, filter, subjectSlot);
        long gameTime = player.level().getGameTime();
        int window = DSConfig.DpsWindowTicks.get();
        StatsEntry fast = fastPathEntry(tracker, filter);
        StatsView session;
        StatsView lifetime;
        if(fast != null) {
            DamageSession current = fast.getCurrentSession();
            // 实时 DPS 是「当下」的量，和看本场还是累计无关，所以两个视图给同一个值
            float realtimeDps = current == null ? 0 : current.getRealtimeDps(gameTime, window);
            session = current == null
                    ? StatsView.EMPTY
                    : view(tracker, current.getAccumulator(), realtimeDps, subjectSlot, typeGrouping);
            lifetime = view(tracker, fast.getLifetime(), realtimeDps, subjectSlot, typeGrouping);
        } else {
            session = fromLog(tracker, filter, subjectSlot, typeGrouping, true, gameTime, window);
            lifetime = fromLog(tracker, filter, subjectSlot, typeGrouping, false, gameTime, window);
        }
        return new StatsSnapshot(
                subject,
                filter,
                subjectSlot,
                typeGrouping,
                labels(tracker, filter),
                session,
                lifetime,
                fast == null ? List.of() : history(fast),
                fast == null && tracker.log().isTruncated(),
                player.hasPermissions(2));
    }

    public static StatsSnapshot snapshotFor(DamageTracker tracker, ServerPlayer player, StatsFilter filter) {
        return snapshotFor(tracker, player, filter, StatsSubjectSlot.SOURCE.resolve(filter),
                DamageTypeGrouping.CATEGORY);
    }

    private static Component subjectName(DamageTracker tracker, StatsFilter filter, StatsSubjectSlot subjectSlot) {
        return switch (subjectSlot) {
            case SOURCE -> filter.source().map(selector -> StatsNames.entitySelector(tracker, selector))
                    .orElseGet(() -> DSKeyLang.ScreenAllDamage.copy());
            case TARGET -> filter.target().map(selector -> StatsNames.entitySelector(tracker, selector))
                    .orElseGet(() -> DSKeyLang.ScreenAllDamage.copy());
            case GLOBAL -> DSKeyLang.ScreenAllDamage.copy();
        };
    }

    /**
     * 只填了一个实体槽的基本视图（我造成的 / 我承受的）直接读预聚合数据，那份是精确且不受日志长度限制的。
     * 一旦掺进直接来源或伤害类型，组合就超出了预聚合能表达的范围，只能回去翻原始记录。
     */
    private static @Nullable StatsEntry fastPathEntry(DamageTracker tracker, StatsFilter filter) {
        if(filter.directSource().isPresent() || filter.damageType().isPresent()) return null;
        if(filter.source().isEmpty() && filter.target().isEmpty()) return tracker.global();
        if(filter.source().isPresent() && filter.target().isEmpty()) {
            return entryFor(tracker, filter.source().get(), true);
        }
        if(filter.target().isPresent() && filter.source().isEmpty()) {
            return entryFor(tracker, filter.target().get(), false);
        }
        return null;
    }

    private static @Nullable StatsEntry entryFor(DamageTracker tracker, EntitySelector selector, boolean outgoing) {
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) ->
                    outgoing ? tracker.outgoing(ref) : tracker.incoming(ref);
            case EntitySelector.Type(ResourceLocation typeId) ->
                    outgoing ? tracker.outgoingByType(typeId) : tracker.incomingByType(typeId);
        };
    }

    /** 从原始记录现聚合。对手维度取「另一边」：筛了目标就看来源，否则看目标 */
    private static StatsView fromLog(DamageTracker tracker, StatsFilter filter, StatsSubjectSlot subjectSlot,
                                     DamageTypeGrouping typeGrouping, boolean sessionOnly,
                                     long gameTime, int windowTicks) {
        long since = sessionOnly ? sessionStart(tracker, filter, subjectSlot, gameTime) : Long.MIN_VALUE;
        List<DamageRecord> matched = tracker.log().matching(filter, since);
        DamageAccumulator.OpponentGrouping grouping = sessionOnly
                ? DamageAccumulator.OpponentGrouping.INSTANCE
                : DamageAccumulator.OpponentGrouping.TYPE;
        DamageAccumulator accumulator = new DamageAccumulator(grouping);
        boolean opponentIsSource = subjectSlot == StatsSubjectSlot.TARGET;
        for (DamageRecord record : matched) {
            accumulator.accept(record, opponentIsSource ? record.source() : record.target());
        }
        return view(tracker, accumulator, windowDps(matched, gameTime, windowTicks), subjectSlot, typeGrouping);
    }

    /** 「本场」优先采用主体预聚合会话的起点，否则从筛选后的原始记录重新切分 */
    private static long sessionStart(DamageTracker tracker, StatsFilter filter,
                                     StatsSubjectSlot subjectSlot, long gameTime) {
        StatsEntry entry = sessionEntry(tracker, filter, subjectSlot);
        if(entry == null) {
            return tracker.log().currentSessionStart(
                    filter, gameTime, DSConfig.SessionTimeoutTicks.get());
        }
        DamageSession session = entry.getCurrentSession();
        return session == null ? gameTime + 1 : session.getStartTime();
    }

    private static @Nullable StatsEntry sessionEntry(DamageTracker tracker, StatsFilter filter,
                                                     StatsSubjectSlot subjectSlot) {
        return switch (subjectSlot) {
            case SOURCE -> filter.source().map(selector -> entryFor(tracker, selector, true)).orElse(null);
            case TARGET -> filter.target().map(selector -> entryFor(tracker, selector, false)).orElse(null);
            case GLOBAL -> tracker.global();
        };
    }

    private static float windowDps(List<DamageRecord> records, long gameTime, int windowTicks) {
        long cutoff = gameTime - windowTicks;
        float sum = 0;
        for (DamageRecord record : records) {
            if(record.gameTime() >= cutoff) sum += record.actualDamage();
        }
        return sum / (windowTicks / DamageAccumulator.TICKS_PER_SECOND);
    }

    public static StatsView view(DamageTracker tracker, DamageAccumulator acc,
                                 float realtimeDps, StatsSubjectSlot subjectSlot,
                                 DamageTypeGrouping typeGrouping) {
        float total = acc.getTotalActual();
        // 筛了目标之后对手维度就翻到来源那一侧，点它应该填来源槽
        boolean opponentIsSource = subjectSlot == StatsSubjectSlot.TARGET;
        boolean typeLevel = acc.getOpponentGrouping() == DamageAccumulator.OpponentGrouping.TYPE;
        List<GroupView> directSources = groups(acc.getByDirectSourceType(), total, StatsNames::entityType,
                typeId -> new FilterKey.Direct(new EntitySelector.Type(typeId)));
        return new StatsView(
                MetricsView.of(acc,
                        StatsNames.damageType(acc.getMaxSingleDamageType()),
                        maxSingleDirectSourceName(tracker, acc),
                        realtimeDps),
                damageTypeGroups(acc.getByDamageType(), total, typeGrouping),
                directSources,
                groups(acc.getByOpponent(), total, ref -> StatsNames.opponent(tracker, ref),
                ref -> opponentKey(ref, typeLevel, opponentIsSource)));
    }

    private static Component maxSingleDirectSourceName(DamageTracker tracker, DamageAccumulator acc) {
        EntityRef directSource = acc.getMaxSingleDirectSource();
        return directSource == null ? Component.literal("-") : StatsNames.opponent(tracker, directSource);
    }

    private static FilterKey opponentKey(EntityRef ref, boolean typeLevel, boolean opponentIsSource) {
        EntitySelector selector = typeLevel
                ? new EntitySelector.Type(ref.typeIdOrEnvironment())
                : new EntitySelector.Instance(ref);
        return opponentIsSource ? new FilterKey.Source(selector) : new FilterKey.Target(selector);
    }

    /**
     * 同一个分类下的多个伤害类型合成一行，否则会冒出两行都叫「魔法伤害」。
     * 合过的行按分类筛，没归类的按注册表 ID 精确筛——正好对上需求里那两种筛法。
     */
    private static List<GroupView> damageTypeGroups(Map<ResourceLocation, DamageAccumulator> byType, float total,
                                                     DamageTypeGrouping typeGrouping) {
        if(typeGrouping == DamageTypeGrouping.REGISTRY) {
            return groups(byType, total, typeId -> Component.literal(typeId.toString()),
                    typeId -> new FilterKey.Type(new DamageTypeSelector.Exact(typeId)));
        }
        Map<DamageTypeSelector, float[]> merged = new LinkedHashMap<>();
        byType.forEach((typeId, group) -> {
            String category = DamageTypeCategories.categoryOf(typeId);
            DamageTypeSelector selector = category == null
                    ? new DamageTypeSelector.Exact(typeId)
                    : new DamageTypeSelector.Category(category);
            float[] sums = merged.computeIfAbsent(selector, key -> new float[2]);
            sums[0] += group.getTotalActual();
            sums[1] += group.getHitCount();
        });
        return merged.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<DamageTypeSelector, float[]> row) -> row.getValue()[0])
                        .reversed())
                .map(row -> new GroupView(
                        StatsNames.damageTypeSelector(row.getKey()),
                        row.getValue()[0],
                        (int) row.getValue()[1],
                        total <= 0 ? 0 : row.getValue()[0] / total,
                        new FilterKey.Type(row.getKey())))
                .toList();
    }

    private static <K> List<GroupView> groups(Map<K, DamageAccumulator> source, float total,
                                              Function<K, Component> namer, Function<K, FilterKey> keyer) {
        return source.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<K, DamageAccumulator> group) -> group.getValue().getTotalActual()).reversed())
                .map(group -> new GroupView(
                        namer.apply(group.getKey()),
                        group.getValue().getTotalActual(),
                        group.getValue().getHitCount(),
                        total <= 0 ? 0 : group.getValue().getTotalActual() / total,
                        keyer.apply(group.getKey())))
                .toList();
    }

    private static List<Component> labels(DamageTracker tracker, StatsFilter filter) {
        List<Component> labels = new ArrayList<>();
        filter.source().ifPresent(selector ->
                labels.add(DSKeyLang.FilterSource.get(StatsNames.entitySelector(tracker, selector))));
        filter.target().ifPresent(selector ->
                labels.add(DSKeyLang.FilterTarget.get(StatsNames.entitySelector(tracker, selector))));
        filter.directSource().ifPresent(selector ->
                labels.add(DSKeyLang.FilterDirect.get(StatsNames.entitySelector(tracker, selector))));
        filter.damageType().ifPresent(selector ->
                labels.add(DSKeyLang.FilterType.get(StatsNames.damageTypeSelector(selector))));
        return labels;
    }

    /** 会话结束后 Overlay 退回展示累计数据，active 为 false 供客户端灰显 */
    public static OverlaySummary overlay(Component targetName, @Nullable StatsEntry entry,
                                         long gameTime, int windowTicks) {
        if(entry == null) return OverlaySummary.empty();
        DamageSession session = entry.getCurrentSession();
        if(session == null) {
            DamageAccumulator lifetime = entry.getLifetime();
            return new OverlaySummary(targetName, lifetime.getTotalActual(), lifetime.getAverageDps(),
                    0, lifetime.getHitCount(), false);
        }
        DamageAccumulator acc = session.getAccumulator();
        return new OverlaySummary(targetName, acc.getTotalActual(), acc.getAverageDps(),
                session.getRealtimeDps(gameTime, windowTicks), acc.getHitCount(), true);
    }

    /** Overlay 按实体类型筛选时使用，类型下的所有目标会合并计算 */
    public static OverlaySummary overlayForOpponentType(Component targetName, @Nullable StatsEntry entry,
                                                        ResourceLocation targetType,
                                                        long gameTime, int windowTicks) {
        if(entry == null) return OverlaySummary.empty();
        DamageSession session = entry.getCurrentSession();
        if(session != null) {
            DamageAccumulator group = session.getOpponentTypeGroup(targetType);
            if(group != null) return new OverlaySummary(targetName, group.getTotalActual(), group.getAverageDps(),
                    session.getOpponentTypeRealtimeDps(targetType, gameTime, windowTicks), group.getHitCount(), true);
        }
        // 本场还没碰过这个类型，退回展示历史累计
        DamageAccumulator lifetimeGroup = entry.getLifetime().opponentGroup(EntityRef.ofType(targetType));
        if(lifetimeGroup == null) return OverlaySummary.empty();
        return new OverlaySummary(targetName, lifetimeGroup.getTotalActual(), lifetimeGroup.getAverageDps(),
                0, lifetimeGroup.getHitCount(), false);
    }

    public static List<SessionView> history(StatsEntry entry) {
        return entry.getFinishedSessions().stream()
                .map(summary -> new SessionView(summary.totalDamage(), summary.averageDps(),
                        summary.durationSeconds(), summary.hitCount()))
                .toList();
    }
}
