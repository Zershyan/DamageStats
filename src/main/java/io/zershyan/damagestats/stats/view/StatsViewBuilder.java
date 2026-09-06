package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.*;
import io.zershyan.damagestats.stats.filter.*;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import io.zershyan.damagestats.stats.focus.FocusScopeView;
import io.zershyan.damagestats.stats.focus.StatsFocus;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

/** 服务端侧把聚合结果转成可传输的视图。名字在这一步就解析好，客户端不必再查注册表 */
public final class StatsViewBuilder {
    private static final int MAX_CURSOR_LENGTH = 128;
    private static final int MAX_CHART_CURSORS = 256;
    private static final Map<DamageEventJournal, LinkedHashMap<String, ChartCursor>> CHART_CURSORS =
            new WeakHashMap<>();
    private static long nextChartSnapshotId;

    private record ChartCursor(
            UUID owner,
            DamageEventJournal journal,
            long journalRevision,
            long directoryRevision,
            long focusVersion,
            StatsFilter filter,
            FocusChartDimension dimension,
            FocusChartScope scope,
            DamageTypeGrouping typeGrouping,
            long snapshotId,
            List<GroupView> groups,
            int start
    ) {
        private ChartCursor at(int newStart) {
            return new ChartCursor(owner, journal, journalRevision, directoryRevision, focusVersion, filter,
                    dimension, scope, typeGrouping, snapshotId, groups, newStart);
        }
    }

    public static StatsSnapshot snapshotFor(DamageTracker tracker, ServerPlayer player, StatsFilter filter,
                                            StatsSubjectSlot subjectSlot, DamageTypeGrouping typeGrouping) {
        Component subject = subjectName(tracker, filter, subjectSlot);
        long gameTime = player.level().getGameTime();
        int window = DSConfig.DpsWindowTicks.get();
        DamageEventJournal journal = ServerStats.journal();
        StatsEntry fast = fastPathEntry(tracker, filter);
        StatsView session;
        StatsView lifetime;
        List<SessionView> history;
        if(journal != null && !(journal.eventCount() == 0 && fast != null)) {
            // 导出与按需查询以完整事件日志为准，旧聚合缓存只作为日志不可用时的兼容后备。
            session = fromHistory(tracker, filter, subjectSlot, typeGrouping, true, gameTime, window);
            lifetime = fromHistory(tracker, filter, subjectSlot, typeGrouping, false, gameTime, window);
            history = historyFromJournal(filter, gameTime);
        } else if(fast != null) {
            DamageSession current = fast.getCurrentSession();
            boolean active = current != null
                    && !current.isTimedOut(gameTime, DSConfig.SessionTimeoutTicks.get());
            // 实时 DPS 是「当下」的量，和看本场还是累计无关，所以两个视图给同一个值
            float realtimeDps = active ? current.getRealtimeDps(gameTime, window) : 0;
            float realtimeOriginalDps = active ? current.getRealtimeOriginalDps(gameTime, window) : 0;
            session = !active
                    ? StatsView.EMPTY
                    : view(tracker, current.getAccumulator(), realtimeDps, realtimeOriginalDps, subjectSlot, typeGrouping);
            lifetime = view(tracker, fast.getLifetime(), realtimeDps, realtimeOriginalDps, subjectSlot, typeGrouping);
            history = history(fast);
        } else {
            session = fromHistory(tracker, filter, subjectSlot, typeGrouping, true, gameTime, window);
            lifetime = fromHistory(tracker, filter, subjectSlot, typeGrouping, false, gameTime, window);
            history = List.of();
        }
        return new StatsSnapshot(
                subject,
                filter,
                labels(tracker, filter),
                session,
                lifetime,
                history);
    }

    public static StatsSnapshot snapshotFor(DamageTracker tracker, ServerPlayer player, StatsFilter filter) {
        return snapshotFor(tracker, player, filter, StatsSubjectSlot.SOURCE.resolve(filter),
                DamageTypeGrouping.CATEGORY);
    }

    /** 焦点摘要只计算页头和 Overlay 所需指标，不构建图表分组或历史列表。 */
    public static FocusSummary focusSummaryFor(DamageTracker tracker, ServerPlayer player, StatsFocus focus) {
        StatsFilter filter = focus.filter();
        long gameTime = player.level().getGameTime();
        int windowTicks = DSConfig.DpsWindowTicks.get();
        Component sourceName = filter.source().map(selector -> StatsNames.entitySelector(tracker, selector))
                .orElseGet(() -> DSKeyLang.ScreenAllDamage.copy());
        Component targetName = filter.target().map(selector -> StatsNames.entitySelector(tracker, selector))
                .orElseGet(() -> DSKeyLang.OverlayAllTargets.copy());
        FocusScopeView scope = new FocusScopeView(focus.version(), focus.source(), focus.target(),
                focus.sourceIsDirectSource(), sourceName, targetName);
        StatsEntry fast = fastPathEntry(tracker, filter);
        if(fast != null) {
            DamageSession current = fast.getCurrentSession();
            boolean active = current != null
                    && !current.isTimedOut(gameTime, DSConfig.SessionTimeoutTicks.get());
            float realtimeDps = active ? current.getRealtimeDps(gameTime, windowTicks) : 0;
            float realtimeOriginalDps = active ? current.getRealtimeOriginalDps(gameTime, windowTicks) : 0;
            FocusMetricsView session = !active ? FocusMetricsView.EMPTY
                    : focusMetrics(tracker, current.getAccumulator(), realtimeDps, realtimeOriginalDps);
            FocusMetricsView lifetime = focusMetrics(tracker, fast.getLifetime(), realtimeDps, realtimeOriginalDps);
            return new FocusSummary(0, scope, session, lifetime, current != null, ServerStats.worldId());
        }
        return focusSummaryFromHistory(tracker, scope, filter, gameTime, windowTicks);
    }

    /** 页间图表按需计算并切成固定页，后台焦点同步不会调用此方法。 */
    public static FocusChartPage focusChartPage(DamageTracker tracker, ServerPlayer player, StatsFocus focus,
                                                StatsFilter filter, FocusChartDimension dimension, FocusChartScope scope,
                                                DamageTypeGrouping typeGrouping, String cursor) {
        boolean allowed = switch (dimension) {
            case RESPONSIBLE_SOURCE -> filter.source().isEmpty() && filter.target().isPresent();
            case TARGET -> filter.source().isPresent() && filter.target().isEmpty();
            case DAMAGE_TYPE, DIRECT_SOURCE -> true;
        };
        if(!allowed) return new FocusChartPage(focus.version(), 0, 0, "", "", false, dimension, scope, typeGrouping,
                List.of(), false);
        StatsSubjectSlot subjectSlot = dimension == FocusChartDimension.RESPONSIBLE_SOURCE
                ? StatsSubjectSlot.TARGET
                : filter.source().isPresent() ? StatsSubjectSlot.SOURCE : StatsSubjectSlot.TARGET;
        long gameTime = player.level().getGameTime();
        DamageEventJournal journal = ServerStats.journal();
        ChartCursor page = cursor == null || cursor.isEmpty()
                ? null : findChartCursor(journal, cursor, player, tracker, focus, filter, dimension, scope, typeGrouping);
        if(cursor != null && !cursor.isEmpty() && page == null) {
            return new FocusChartPage(focus.version(), 0, 0, "", "", true, dimension, scope, typeGrouping,
                    List.of(), false);
        }
        String currentCursor = cursor == null ? "" : cursor;
        if(page == null) {
            StatsEntry fast = fastPathEntry(tracker, filter);
            // 新版日志有数据时始终从日志读取；只有旧版仍留有聚合缓存而没有事件日志时才回退。
            StatsView view = fast != null && (journal == null || journal.eventCount() == 0)
                    ? cachedView(tracker, fast, subjectSlot, typeGrouping, scope, gameTime)
                    : fromHistory(tracker, filter, subjectSlot, typeGrouping,
                            scope == FocusChartScope.SESSION, gameTime, DSConfig.DpsWindowTicks.get());
            List<GroupView> groups = switch (dimension) {
                case DAMAGE_TYPE -> view.byType();
                case DIRECT_SOURCE -> view.bySource();
                case RESPONSIBLE_SOURCE, TARGET -> view.byOpponent();
            };
            if(journal == null) {
                int end = Math.min(groups.size(), FocusChartPage.PAGE_SIZE);
                return new FocusChartPage(focus.version(), 0, 1, "", "", true, dimension, scope, typeGrouping,
                        groups.subList(0, end), end < groups.size());
            }
            page = new ChartCursor(player.getUUID(), journal, journal.queryRevision(),
                    tracker.instanceDirectory().revision(), focus.version(), filter, dimension, scope, typeGrouping,
                    ++nextChartSnapshotId, List.copyOf(groups), 0);
            currentCursor = UUID.randomUUID().toString();
            storeChartCursor(currentCursor, page);
        }
        List<GroupView> groups = page.groups();
        int start = Math.clamp(page.start(), 0, groups.size());
        int end = Math.min(groups.size(), start + FocusChartPage.PAGE_SIZE);
        String nextCursor = "";
        if(end < groups.size()) {
            nextCursor = UUID.randomUUID().toString();
            storeChartCursor(nextCursor, page.at(end));
        }
        return new FocusChartPage(focus.version(), 0, page.snapshotId(), currentCursor, nextCursor, true, dimension, scope,
                typeGrouping, groups.subList(start, end), end < groups.size());
    }

    public static FocusChartPage deniedFocusChartPage(StatsFocus focus, FocusChartDimension dimension,
                                                       FocusChartScope scope, DamageTypeGrouping typeGrouping) {
        return new FocusChartPage(focus.version(), 0, 0, "", "", false, dimension, scope, typeGrouping, List.of(), false);
    }

    private static ChartCursor findChartCursor(DamageEventJournal journal, String cursor, ServerPlayer player,
                                               DamageTracker tracker, StatsFocus focus, StatsFilter filter,
                                               FocusChartDimension dimension, FocusChartScope scope,
                                               DamageTypeGrouping typeGrouping) {
        if(journal == null || cursor.length() > MAX_CURSOR_LENGTH) return null;
        LinkedHashMap<String, ChartCursor> cursors = CHART_CURSORS.get(journal);
        if(cursors == null) return null;
        ChartCursor page = cursors.get(cursor);
        if(page == null || page.owner().equals(player.getUUID()) == false) return null;
        if(page.journal() != journal || page.journalRevision() != journal.queryRevision()
                || page.directoryRevision() != tracker.instanceDirectory().revision()
                || page.focusVersion() != focus.version() || !page.filter().equals(filter)
                || page.dimension() != dimension || page.scope() != scope || page.typeGrouping() != typeGrouping) {
            return null;
        }
        return page;
    }

    private static void storeChartCursor(String cursor, ChartCursor page) {
        LinkedHashMap<String, ChartCursor> cursors = CHART_CURSORS.computeIfAbsent(page.journal(), ignored ->
                new LinkedHashMap<>(16, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, ChartCursor> eldest) {
                        return size() > MAX_CHART_CURSORS;
                    }
                });
        cursors.put(cursor, page);
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
        if(filter.sourceIsDirectSource()) return null;
        if(filter.directSource().isPresent() || filter.damageType().isPresent()) return null;
        if(filter.source().isEmpty() && filter.target().isEmpty()) {
            return tracker.global().getLifetime().getHitCount() == 0 ? null : tracker.global();
        }
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

    /** 从完整原始事件历史现聚合。对手维度取「另一边」：筛了目标就看来源，否则看目标 */
    private static StatsView fromHistory(DamageTracker tracker, StatsFilter filter, StatsSubjectSlot subjectSlot,
                                          DamageTypeGrouping typeGrouping, boolean sessionOnly,
                                          long gameTime, int windowTicks) {
        DamageEventJournal journal = ServerStats.journal();
        boolean opponentIsSource = subjectSlot == StatsSubjectSlot.TARGET;
        if(journal == null) return StatsView.EMPTY;
        DamageEventJournal.QueryResult query = journal.query(filter);
        int timeoutTicks = DSConfig.SessionTimeoutTicks.get();
        boolean active = query.isSessionActive(gameTime, timeoutTicks);
        DamageAccumulator accumulator = sessionOnly
                ? active ? query.session(opponentIsSource, timeoutTicks)
                        : new DamageAccumulator(DamageAccumulator.OpponentGrouping.INSTANCE)
                : query.lifetime(opponentIsSource);
        float realtimeDps = !active ? 0 : sessionOnly
                ? query.currentSessionDps(gameTime, windowTicks, timeoutTicks)
                : query.realtimeDps(gameTime, windowTicks);
        float realtimeOriginalDps = !active ? 0 : sessionOnly
                ? query.currentSessionOriginalDps(gameTime, windowTicks, timeoutTicks)
                : query.realtimeOriginalDps(gameTime, windowTicks);
        return sessionOnly && !active ? StatsView.EMPTY
                : view(tracker, accumulator, realtimeDps, realtimeOriginalDps, subjectSlot, typeGrouping);
    }

    /** 「本场」优先采用主体预聚合会话的起点，否则从筛选后的原始记录重新切分 */
    private static long sessionStart(DamageTracker tracker, StatsFilter filter,
                                     StatsSubjectSlot subjectSlot, long gameTime) {
        StatsEntry entry = sessionEntry(tracker, filter, subjectSlot);
        if(entry == null) {
            DamageEventJournal journal = ServerStats.journal();
            if(journal == null) return gameTime + 1;
            return journal.query(filter).currentSessionStart(gameTime, DSConfig.SessionTimeoutTicks.get());
        }
        DamageSession session = entry.getCurrentSession();
        return session == null || session.isTimedOut(gameTime, DSConfig.SessionTimeoutTicks.get())
                ? gameTime + 1 : session.getStartTime();
    }

    /** 从权威事件历史恢复最近已结束会话，避免旧聚合缓存关闭保存后丢失历史页和导出内容。 */
    public static List<SessionView> historyFromJournal(StatsFilter filter, long gameTime) {
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null || DSConfig.KeepFinishedSessions.get() == 0) return List.of();
        return journal.query(filter).finishedSessions(gameTime, DSConfig.SessionTimeoutTicks.get(),
                        DSConfig.KeepFinishedSessions.get()).stream()
                .map(session -> new SessionView(session.totalDamage(), session.averageDps(),
                        session.durationTicks() / DamageAccumulator.TICKS_PER_SECOND, session.hitCount()))
                .toList();
    }

    private static FocusSummary focusSummaryFromHistory(DamageTracker tracker, FocusScopeView scope,
                                                        StatsFilter filter, long gameTime, int windowTicks) {
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return new FocusSummary(0, scope, FocusMetricsView.EMPTY, FocusMetricsView.EMPTY, false,
                ServerStats.worldId());
        DamageEventJournal.QueryResult query = journal.query(filter);
        int timeoutTicks = DSConfig.SessionTimeoutTicks.get();
        boolean active = query.isSessionActive(gameTime, timeoutTicks);
        DamageAccumulator lifetime = query.lifetime(false);
        DamageAccumulator session = active
                ? query.session(false, timeoutTicks)
                : new DamageAccumulator(DamageAccumulator.OpponentGrouping.INSTANCE);
        float realtimeDps = active ? query.realtimeDps(gameTime, windowTicks) : 0;
        float realtimeOriginalDps = active ? query.realtimeOriginalDps(gameTime, windowTicks) : 0;
        return new FocusSummary(0, scope,
                active ? focusMetrics(tracker, session, realtimeDps, realtimeOriginalDps) : FocusMetricsView.EMPTY,
                focusMetrics(tracker, lifetime, realtimeDps, realtimeOriginalDps),
                active, ServerStats.worldId());
    }

    private static @Nullable StatsEntry sessionEntry(DamageTracker tracker, StatsFilter filter,
                                                     StatsSubjectSlot subjectSlot) {
        if(filter.sourceIsDirectSource() || filter.directSource().isPresent() || filter.damageType().isPresent()
                || (filter.source().isPresent() && filter.target().isPresent())) return null;
        return switch (subjectSlot) {
            case SOURCE -> filter.source().map(selector -> entryFor(tracker, selector, true)).orElse(null);
            case TARGET -> filter.target().map(selector -> entryFor(tracker, selector, false)).orElse(null);
            case GLOBAL -> tracker.global();
        };
    }

    private static StatsView cachedView(DamageTracker tracker, StatsEntry entry, StatsSubjectSlot subjectSlot,
                                        DamageTypeGrouping typeGrouping, FocusChartScope scope, long gameTime) {
        DamageSession current = entry.getCurrentSession();
        boolean active = current != null
                && !current.isTimedOut(gameTime, DSConfig.SessionTimeoutTicks.get());
        float realtimeDps = active
                ? current.getRealtimeDps(gameTime, DSConfig.DpsWindowTicks.get()) : 0;
        float realtimeOriginalDps = active
                ? current.getRealtimeOriginalDps(gameTime, DSConfig.DpsWindowTicks.get()) : 0;
        if(scope == FocusChartScope.SESSION) {
            return !active ? StatsView.EMPTY
                    : view(tracker, current.getAccumulator(), realtimeDps, realtimeOriginalDps,
                    subjectSlot, typeGrouping);
        }
        return view(tracker, entry.getLifetime(), realtimeDps, realtimeOriginalDps, subjectSlot, typeGrouping);
    }

    public static StatsView view(DamageTracker tracker, DamageAccumulator acc,
                                 float realtimeDps, float realtimeOriginalDps, StatsSubjectSlot subjectSlot,
                                 DamageTypeGrouping typeGrouping) {
        float total = acc.getTotalActual();
        // 筛了目标之后对手维度就翻到来源那一侧，点它应该填来源槽
        boolean opponentIsSource = subjectSlot == StatsSubjectSlot.TARGET;
        boolean typeLevel = acc.getOpponentGrouping() == DamageAccumulator.OpponentGrouping.TYPE;
        List<GroupView> directSources = groups(tracker, acc.getByDirectSourceType(), total, StatsNames::entityType,
                typeId -> new FilterKey.Direct(new EntitySelector.Type(typeId)));
        return new StatsView(
                metrics(tracker, acc, realtimeDps, realtimeOriginalDps),
                damageTypeGroups(tracker, acc.getByDamageType(), total, typeGrouping),
                directSources,
                groups(tracker, acc.getByOpponent(), total, ref -> StatsNames.opponent(tracker, ref),
                ref -> opponentKey(ref, typeLevel, opponentIsSource)));
    }

    private static MetricsView metrics(DamageTracker tracker, DamageAccumulator accumulator,
                                       float realtimeDps, float realtimeOriginalDps) {
        return MetricsView.of(accumulator,
                StatsNames.damageType(accumulator.getMaxSingleDamageType()),
                maxSingleDirectSourceName(tracker, accumulator),
                realtimeDps, realtimeOriginalDps);
    }

    /** 焦点缓存与按需构建视图复用同一套指标及最高贡献项计算，保证两条路径结果一致。 */
    public static FocusMetricsView focusMetrics(DamageTracker tracker, DamageAccumulator accumulator,
                                                float realtimeDps, float realtimeOriginalDps) {
        return new FocusMetricsView(metrics(tracker, accumulator, realtimeDps, realtimeOriginalDps),
                topContribution(accumulator.getByDamageType(), accumulator.getTotalActual(), StatsNames::damageType),
                topContribution(accumulator.getByDirectSourceType(), accumulator.getTotalActual(), StatsNames::entityType));
    }

    private static <K> ContributionView topContribution(Map<K, DamageAccumulator> groups, float total,
                                                         Function<K, Component> namer) {
        Map.Entry<K, DamageAccumulator> top = groups.entrySet().stream()
                .max(Comparator.comparingDouble(entry -> entry.getValue().getTotalActual()))
                .orElse(null);
        if(top == null) return ContributionView.EMPTY;
        float damage = top.getValue().getTotalActual();
        return new ContributionView(namer.apply(top.getKey()), damage, total <= 0 ? 0 : damage / total);
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
    private static List<GroupView> damageTypeGroups(DamageTracker tracker, Map<ResourceLocation, DamageAccumulator> byType, float total,
                                                     DamageTypeGrouping typeGrouping) {
        if(typeGrouping == DamageTypeGrouping.REGISTRY) {
            return groups(tracker, byType, total, typeId -> Component.literal(typeId.toString()),
                    typeId -> new FilterKey.Type(new DamageTypeSelector.Exact(typeId)));
        }
        Map<DamageTypeSelector, float[]> merged = new LinkedHashMap<>();
        byType.forEach((typeId, group) -> {
            String category = DamageTypeCategories.categoryOf(typeId);
            DamageTypeSelector selector = category == null
                    ? new DamageTypeSelector.Exact(typeId)
                    : new DamageTypeSelector.Category(category);
            float[] sums = merged.computeIfAbsent(selector, key -> new float[3]);
            sums[0] += group.getTotalActual();
            sums[1] += group.getTotalOriginal();
            sums[2] += group.getHitCount();
        });
        return merged.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<DamageTypeSelector, float[]> row) -> row.getValue()[0])
                        .reversed())
                .map(row -> new GroupView(
                        StatsNames.damageTypeSelector(row.getKey()),
                        row.getValue()[0],
                        row.getValue()[1],
                        (int) row.getValue()[2],
                        total <= 0 ? 0 : row.getValue()[0] / total,
                        new FilterKey.Type(row.getKey()),
                        false))
                .toList();
    }

    private static <K> List<GroupView> groups(DamageTracker tracker, Map<K, DamageAccumulator> source, float total,
                                              Function<K, Component> namer, Function<K, FilterKey> keyer) {
        return source.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<K, DamageAccumulator> group) -> group.getValue().getTotalActual()).reversed())
                .map(group -> {
                    FilterKey key = keyer.apply(group.getKey());
                    return new GroupView(
                            namer.apply(group.getKey()),
                            group.getValue().getTotalActual(),
                            group.getValue().getTotalOriginal(),
                            group.getValue().getHitCount(),
                            total <= 0 ? 0 : group.getValue().getTotalActual() / total,
                            key,
                            canOpenInstances(tracker, key));
                })
                .toList();
    }

    private static boolean canOpenInstances(DamageTracker tracker, FilterKey key) {
        EntitySelector selector = switch (key) {
            case FilterKey.Source(EntitySelector value) -> value;
            case FilterKey.Target(EntitySelector value) -> value;
            case FilterKey.Direct(EntitySelector value) -> value;
            case FilterKey.Type ignored -> null;
        };
        if(selector == null) return false;
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) -> tracker.instanceDirectory().contains(ref);
            case EntitySelector.Type(ResourceLocation typeId) -> tracker.instanceDirectory().containsType(typeId);
        };
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

    public static List<SessionView> history(StatsEntry entry) {
        return entry.getFinishedSessions().stream()
                .map(summary -> new SessionView(summary.totalDamage(), summary.averageDps(),
                        summary.durationSeconds(), summary.hitCount()))
                .toList();
    }
}
