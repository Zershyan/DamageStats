package io.zershyan.damagestats.stats;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;

/**
 * 一个统计对象的全部数据：当前会话、最近若干场已结束的会话、以及永久累计。
 * 归属于谁、是造成伤害还是承受伤害，都由持有它的 {@link DamageTracker} 用 Map 的 key 表达。
 */
public class StatsEntry {
    private final DamageAccumulator lifetime = new DamageAccumulator();
    private final Deque<DamageSession> finishedSessions = new ArrayDeque<>();
    private @Nullable DamageSession currentSession;
    private long lastActivityTime;

    public void accept(DamageRecord record, EntityRef opponent) {
        if(currentSession == null) currentSession = new DamageSession(record.gameTime());
        currentSession.accept(record, opponent);
        lifetime.accept(record, opponent);
        lastActivityTime = record.gameTime();
    }

    public void tick(long currentGameTime, int timeoutTicks, int keepSessions) {
        if(currentSession == null) return;
        if(!currentSession.isTimedOut(currentGameTime, timeoutTicks)) return;
        currentSession.finish();
        if(keepSessions > 0) finishedSessions.addLast(currentSession);
        while(finishedSessions.size() > keepSessions) finishedSessions.removeFirst();
        currentSession = null;
    }

    public DamageAccumulator getLifetime() { return lifetime; }
    public @Nullable DamageSession getCurrentSession() { return currentSession; }
    public long getLastActivityTime() { return lastActivityTime; }

    public Collection<DamageSession> getFinishedSessions() {
        return Collections.unmodifiableCollection(finishedSessions);
    }
}
