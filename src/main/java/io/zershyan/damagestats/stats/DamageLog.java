package io.zershyan.damagestats.stats;

import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;

import java.util.*;

/**
 * 原始伤害记录的环形日志。四个筛选槽位可以任意组合，预聚合的分组撑不住这种笛卡尔积，
 * 所以带筛选的查询一律回来翻这份日志。
 * 它刻意不持久化：这是给「最近这段仗打得怎么样」用的，跨存档回看那部分由预聚合的累计数据负责。
 */
public class DamageLog {
    private record LoggedRecord(long sequence, DamageRecord record) {}

    private final Deque<LoggedRecord> records = new ArrayDeque<>();
    private final Map<UUID, Long> resetSequences = new HashMap<>();
    private long nextSequence;
    private boolean truncated;

    public void accept(DamageRecord record, int limit) {
        records.addLast(new LoggedRecord(nextSequence++, record));
        while(records.size() > limit) {
            records.removeFirst();
            truncated = true;
        }
    }

    public List<DamageRecord> matching(StatsFilter filter, long sinceGameTime) {
        return records.stream()
                .filter(logged -> visibleToFilter(logged, filter))
                .map(LoggedRecord::record)
                .filter(record -> record.gameTime() >= sinceGameTime)
                .filter(filter::matches)
                .toList();
    }

    /** 取当前筛选条件最后一场尚未超时的战斗起点，给无法走预聚合的查询使用 */
    public long currentSessionStart(StatsFilter filter, long gameTime, int timeoutTicks) {
        long start = -1;
        long last = -1;
        for (LoggedRecord logged : records) {
            DamageRecord record = logged.record();
            if(!visibleToFilter(logged, filter) || !filter.matches(record)) continue;
            if(last < 0 || record.gameTime() - last > timeoutTicks) start = record.gameTime();
            last = record.gameTime();
        }
        return last < 0 || gameTime - last > timeoutTicks ? gameTime + 1 : start;
    }

    /** 淘汰过记录，说明按筛选条件查出来的数字可能不完整，界面上要提示 */
    public boolean isTruncated() {
        return truncated;
    }

    public void clear() {
        records.clear();
        resetSequences.clear();
        nextSequence = 0;
        truncated = false;
    }

    /** 只标记实例重置边界，保留原始记录供其他实体的统计继续查询 */
    public void markReset(EntityRef owner) {
        resetSequences.put(owner.id(), nextSequence);
    }

    private boolean visibleToFilter(LoggedRecord logged, StatsFilter filter) {
        return !clearedBefore(logged, filter.source(), logged.record().source())
                && !clearedBefore(logged, filter.target(), logged.record().target())
                && !clearedBefore(logged, filter.directSource(), logged.record().directSource());
    }

    private boolean clearedBefore(LoggedRecord logged, Optional<EntitySelector> selector,
                                  EntityRef candidate) {
        if(!(selector.orElse(null) instanceof EntitySelector.Instance instance)) return false;
        if(!instance.ref().id().equals(candidate.id())) return false;
        Long resetSequence = resetSequences.get(candidate.id());
        return resetSequence != null && logged.sequence() < resetSequence;
    }
}
