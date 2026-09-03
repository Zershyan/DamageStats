package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 一个统计对象的全部数据：进行中的会话、最近若干场已结束战斗的摘要、以及永久累计。
 * 归属于谁、是造成伤害还是承受伤害，都由持有它的 {@link DamageTracker} 用 Map 的 key 表达。
 * 进行中的会话刻意不持久化——一场战斗跨不过重启，存下来也没有意义。
 */
public class StatsEntry {
    public static final Codec<StatsEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DamageAccumulator.CODEC.fieldOf("lifetime").forGetter(StatsEntry::getLifetime),
            SessionSummary.CODEC.listOf().optionalFieldOf("history", List.of())
                    .forGetter(entry -> List.copyOf(entry.finishedSessions)),
            Codec.LONG.optionalFieldOf("lastActivity", 0L).forGetter(StatsEntry::getLastActivityTime)
    ).apply(instance, StatsEntry::new));

    private final DamageAccumulator lifetime;
    private final Deque<SessionSummary> finishedSessions = new ArrayDeque<>();
    private @Nullable DamageSession currentSession;
    private long lastActivityTime;

    public StatsEntry() {
        this.lifetime = new DamageAccumulator(DamageAccumulator.OpponentGrouping.TYPE);
    }

    private StatsEntry(DamageAccumulator lifetime, List<SessionSummary> history, long lastActivityTime) {
        this.lifetime = lifetime;
        this.finishedSessions.addAll(history);
        this.lastActivityTime = lastActivityTime;
    }

    public void accept(DamageRecord record, EntityRef opponent) {
        if(currentSession == null) currentSession = new DamageSession(record.gameTime());
        currentSession.accept(record, opponent);
        lifetime.accept(record, opponent);
        lastActivityTime = record.gameTime();
    }

    public void tick(long currentGameTime, int timeoutTicks, int keepSessions) {
        if(currentSession == null) return;
        if(!currentSession.isTimedOut(currentGameTime, timeoutTicks)) return;
        if(keepSessions > 0) finishedSessions.addLast(currentSession.summarize());
        while(finishedSessions.size() > keepSessions) finishedSessions.removeFirst();
        currentSession = null;
    }

    public DamageAccumulator getLifetime() { return lifetime; }
    public @Nullable DamageSession getCurrentSession() { return currentSession; }
    public long getLastActivityTime() { return lastActivityTime; }

    public Collection<SessionSummary> getFinishedSessions() {
        return Collections.unmodifiableCollection(finishedSessions);
    }

    /** 只合并永久累计，不把不同来源的会话历史拼成一场不存在的战斗 */
    void absorbLifetime(StatsEntry other) {
        lifetime.absorb(other.lifetime);
        lastActivityTime = Math.max(lastActivityTime, other.lastActivityTime);
    }
}
