package io.zershyan.damagestats.stats.focus;

import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.*;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import io.zershyan.damagestats.stats.view.FocusMetricsView;
import io.zershyan.damagestats.stats.view.FocusSummary;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * 一个玩家当前焦点的增量聚合。创建或切换焦点时才扫描历史，之后只接收已匹配的新增伤害，
 * 避免每次命中都重新读取完整事件分段。
 */
public final class FocusSummaryCache {
    private final StatsFocus focus;
    private final FocusScopeView scope;
    private final DamageAccumulator lifetime = new DamageAccumulator(DamageAccumulator.OpponentGrouping.TYPE);
    private @Nullable DamageSession session;

    private FocusSummaryCache(StatsFocus focus, FocusScopeView scope) {
        this.focus = focus;
        this.scope = scope;
    }

    public static FocusSummaryCache empty(DamageTracker tracker, StatsFocus focus) {
        return new FocusSummaryCache(focus, scope(tracker, focus));
    }

    public static FocusSummaryCache load(DamageTracker tracker, ServerPlayer player, StatsFocus focus) {
        FocusSummaryCache cache = new FocusSummaryCache(focus, scope(tracker, focus));
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return cache;
        List<DamageRecord> records = journal.matching(focus.filter()).stream()
                .sorted(Comparator.comparingLong(DamageRecord::gameTime))
                .toList();
        records.forEach(record -> cache.lifetime.accept(record, record.target()));
        cache.restoreSession(records, player.level().getGameTime());
        return cache;
    }

    public void accept(DamageRecord record) {
        lifetime.accept(record, record.target());
        if(session == null || session.isTimedOut(record.gameTime(), DSConfig.SessionTimeoutTicks.get())) {
            session = new DamageSession(record.gameTime());
        }
        session.accept(record, record.target());
    }

    public boolean expire(long gameTime) {
        if(session == null || !session.isTimedOut(gameTime, DSConfig.SessionTimeoutTicks.get())) return false;
        session = null;
        return true;
    }

    public FocusSummary summary(DamageTracker tracker, long gameTime) {
        DamageSession current = session;
        if(current != null && current.isTimedOut(gameTime, DSConfig.SessionTimeoutTicks.get())) {
            session = null;
            current = null;
        }
        float realtimeDps = current == null ? 0 : current.getRealtimeDps(gameTime, DSConfig.DpsWindowTicks.get());
        float realtimeOriginalDps = current == null ? 0
                : current.getRealtimeOriginalDps(gameTime, DSConfig.DpsWindowTicks.get());
        FocusMetricsView sessionView = current == null ? FocusMetricsView.EMPTY
                : StatsViewBuilder.focusMetrics(tracker, current.getAccumulator(), realtimeDps, realtimeOriginalDps);
        FocusMetricsView lifetimeView = StatsViewBuilder.focusMetrics(tracker, lifetime, realtimeDps, realtimeOriginalDps);
        return new FocusSummary(0, scope, sessionView, lifetimeView, current != null, ServerStats.worldId());
    }

    private void restoreSession(List<DamageRecord> records, long gameTime) {
        if(records.isEmpty()) return;
        int start = 0;
        int timeout = DSConfig.SessionTimeoutTicks.get();
        for (int i = 1; i < records.size(); i++) {
            if(records.get(i).gameTime() - records.get(i - 1).gameTime() > timeout) start = i;
        }
        DamageRecord latest = records.getLast();
        if(latest.gameTime() + timeout < gameTime) return;
        session = new DamageSession(records.get(start).gameTime());
        for (int i = start; i < records.size(); i++) session.accept(records.get(i), records.get(i).target());
    }

    private static FocusScopeView scope(DamageTracker tracker, StatsFocus focus) {
        Component sourceName = focus.source().map(selector -> StatsNames.entitySelector(tracker, selector))
                .orElseGet(() -> DSKeyLang.ScreenAllDamage.copy());
        Component targetName = focus.target().map(selector -> StatsNames.entitySelector(tracker, selector))
                .orElseGet(() -> DSKeyLang.OverlayAllTargets.copy());
        return new FocusScopeView(focus.version(), focus.source(), focus.target(), focus.sourceIsDirectSource(),
                sourceName, targetName);
    }
}
