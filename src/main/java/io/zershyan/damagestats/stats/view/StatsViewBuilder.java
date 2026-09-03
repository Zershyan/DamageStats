package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.*;
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** 服务端侧把聚合结果转成可传输的视图。名字在这一步就解析好，客户端不必再查注册表 */
public final class StatsViewBuilder {
    public static EntryView entry(DamageTracker tracker, Component ownerName, @Nullable StatsEntry entry,
                                  long gameTime, int windowTicks) {
        if(entry == null) return EntryView.empty(ownerName);
        DamageSession session = entry.getCurrentSession();
        StatsView sessionView = session == null
                ? StatsView.EMPTY
                : view(tracker, session.getAccumulator(), session.getRealtimeDps(gameTime, windowTicks));
        return new EntryView(ownerName, sessionView, view(tracker, entry.getLifetime(), 0));
    }

    public static StatsView view(DamageTracker tracker, DamageAccumulator acc, float realtimeDps) {
        float total = acc.getTotalActual();
        return new StatsView(
                MetricsView.of(acc, StatsNames.damageType(acc.getMaxSingleDamageType()), realtimeDps),
                groups(acc.getByDamageType(), StatsNames::damageType, total),
                groups(acc.getByDirectSourceType(), StatsNames::entityType, total),
                groups(acc.getByOpponent(), ref -> StatsNames.opponent(tracker, ref), total));
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

    /** Overlay 盯着某个具体目标时用这个：伤害和命中次数都只算对它的那部分 */
    public static OverlaySummary overlayForOpponent(Component targetName, @Nullable StatsEntry entry,
                                                    EntityRef opponent, long gameTime, int windowTicks) {
        if(entry == null) return OverlaySummary.empty();
        DamageSession session = entry.getCurrentSession();
        if(session != null) {
            DamageAccumulator group = session.getOpponentGroup(opponent);
            if(group != null) return new OverlaySummary(targetName, group.getTotalActual(), group.getAverageDps(),
                    session.getOpponentRealtimeDps(opponent, gameTime, windowTicks), group.getHitCount(), true);
        }
        // 本场还没碰过这个目标，退回展示历史累计
        DamageAccumulator lifetimeGroup = entry.getLifetime().getByOpponent().get(opponent);
        if(lifetimeGroup == null) return OverlaySummary.empty();
        return new OverlaySummary(targetName, lifetimeGroup.getTotalActual(), lifetimeGroup.getAverageDps(),
                0, lifetimeGroup.getHitCount(), false);
    }

    public static List<SessionView> history(StatsEntry entry) {
        return entry.getFinishedSessions().stream().map(session -> {
            DamageAccumulator acc = session.getAccumulator();
            return new SessionView(acc.getTotalActual(), acc.getAverageDps(),
                    acc.getDurationTicks() / DamageAccumulator.TICKS_PER_SECOND, acc.getHitCount());
        }).toList();
    }

    private static <K> List<GroupView> groups(Map<K, DamageAccumulator> source,
                                              Function<K, Component> namer, float total) {
        return source.entrySet().stream()
                .sorted(Comparator.comparingDouble(
                        (Map.Entry<K, DamageAccumulator> group) -> group.getValue().getTotalActual()).reversed())
                .map(group -> new GroupView(
                        namer.apply(group.getKey()),
                        group.getValue().getTotalActual(),
                        group.getValue().getHitCount(),
                        total <= 0 ? 0 : group.getValue().getTotalActual() / total))
                .toList();
    }

    private StatsViewBuilder() {
    }
}
