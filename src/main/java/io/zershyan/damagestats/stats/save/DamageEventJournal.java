package io.zershyan.damagestats.stats.save;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.stats.*;
import io.zershyan.damagestats.stats.filter.DamageTypeSelector;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 完整原始伤害事件的二进制分段存储。
 * 每条记录都有长度与校验和，异常停机时读取器会停在最后一条完整帧；段索引只是查询加速器，
 * 丢失或损坏后可以由段文件重建，永远不作为唯一数据源。
 */
public final class DamageEventJournal {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIRECTORY_NAME = "events";
    private static final String INDEX_FILE_NAME = "index.bin";
    private static final String RESET_FILE_NAME = "resets.bin";
    private static final int SEGMENT_MAGIC = 0x44534A31;
    private static final int INDEX_MAGIC = 0x44534931;
    private static final int FORMAT_VERSION = 1;
    private static final int SEGMENT_HEADER_SIZE = Integer.BYTES * 2;
    private static final int SEGMENT_RECORD_LIMIT = 512;
    private static final int MAX_FRAME_SIZE = 16 * 1024;
    private static final Pattern SEGMENT_NAME = Pattern.compile("segment-(\\d+)\\.bin");
    private static final Pattern COMPRESSED_SEGMENT_NAME = Pattern.compile("segment-(\\d+)\\.bin\\.gz");
    private static final Pattern LEGACY_SEGMENT_NAME = Pattern.compile("segment-\\d+-\\d+\\.jsonl");

    /** 旧 JSONL 仅用于一次性迁移，成功后会保留为 .migrated 文件供管理员核对。 */
    private record LegacyJournalEntry(long sequence, DamageRecord record) {
        private static final Codec<LegacyJournalEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("sequence").forGetter(LegacyJournalEntry::sequence),
                DamageRecord.CODEC.fieldOf("record").forGetter(LegacyJournalEntry::record)
        ).apply(instance, LegacyJournalEntry::new));
    }

    private record JournalEntry(long sequence, DamageRecord record) {}

    public enum ContributionDimension {
        SOURCE,
        TARGET,
        DIRECT_SOURCE
    }

    public record DamageContribution(float damage, int hitCount) {
        private DamageContribution add(DamageContribution other) {
            return new DamageContribution(damage + other.damage, hitCount + other.hitCount);
        }
    }

    public record SessionAggregate(float totalDamage, float averageDps, long durationTicks,
                                   int hitCount, long lastGameTime) {}

    /**
     * 一次筛选的不可变事件快照。第一次查询时建立，图表、历史和实例选择共用它，
     * 避免每个页面再次读取事件段并重复聚合。
     */
    public static final class QueryResult {
        private final List<DamageRecord> records;
        private final long[] gameTimes;
        private final double[] actualPrefix;
        private final double[] originalPrefix;
        private final DamageAccumulator outgoingLifetime;
        private final DamageAccumulator incomingLifetime;
        private final Map<Integer, TemporalResult> temporalResults = new HashMap<>();
        private final EnumMap<ContributionDimension, Map<UUID, DamageContribution>> contributions =
                new EnumMap<>(ContributionDimension.class);

        private QueryResult(List<JournalEntry> matched) {
            List<JournalEntry> ordered = matched.stream()
                    .sorted(Comparator.comparingLong((JournalEntry entry) -> entry.record().gameTime())
                            .thenComparingLong(JournalEntry::sequence))
                    .toList();
            records = ordered.stream().map(JournalEntry::record).toList();
            gameTimes = new long[records.size()];
            actualPrefix = new double[records.size() + 1];
            originalPrefix = new double[records.size() + 1];
            outgoingLifetime = new DamageAccumulator(DamageAccumulator.OpponentGrouping.TYPE);
            incomingLifetime = new DamageAccumulator(DamageAccumulator.OpponentGrouping.TYPE);
            for (int index = 0; index < records.size(); index++) {
                DamageRecord record = records.get(index);
                gameTimes[index] = record.gameTime();
                actualPrefix[index + 1] = actualPrefix[index] + record.actualDamage();
                originalPrefix[index + 1] = originalPrefix[index] + record.originalDamage();
                outgoingLifetime.accept(record, record.target());
                incomingLifetime.accept(record, record.source());
            }
        }

        public List<DamageRecord> records() {
            return records;
        }

        public DamageAccumulator lifetime(boolean opponentIsSource) {
            return opponentIsSource ? incomingLifetime : outgoingLifetime;
        }

        public DamageAccumulator lifetime(boolean opponentIsSource, DamageAccumulator.OpponentGrouping grouping) {
            if(grouping == DamageAccumulator.OpponentGrouping.TYPE) return lifetime(opponentIsSource);
            return aggregate(grouping, opponentIsSource, 0, records.size());
        }

        public synchronized DamageAccumulator session(boolean opponentIsSource, int timeoutTicks) {
            TemporalResult temporal = temporal(timeoutTicks);
            return opponentIsSource ? temporal.incomingSession : temporal.outgoingSession;
        }

        public synchronized DamageAccumulator session(boolean opponentIsSource, int timeoutTicks,
                                                       DamageAccumulator.OpponentGrouping grouping) {
            if(grouping == DamageAccumulator.OpponentGrouping.INSTANCE) {
                return session(opponentIsSource, timeoutTicks);
            }
            TemporalResult temporal = temporal(timeoutTicks);
            if(temporal.currentStartIndex < 0) return new DamageAccumulator(grouping);
            return aggregate(grouping, opponentIsSource, temporal.currentStartIndex, records.size());
        }

        private DamageAccumulator aggregate(DamageAccumulator.OpponentGrouping grouping,
                                             boolean opponentIsSource, int start, int end) {
            DamageAccumulator result = new DamageAccumulator(grouping);
            int from = Math.clamp(start, 0, records.size());
            int to = Math.clamp(end, from, records.size());
            for(int index = from; index < to; index++) {
                DamageRecord record = records.get(index);
                result.accept(record, opponentIsSource ? record.source() : record.target());
            }
            return result;
        }

        public boolean isSessionActive(long gameTime, int timeoutTicks) {
            if(records.isEmpty()) return false;
            return gameTime - gameTimes[gameTimes.length - 1] <= timeoutTicks;
        }

        public synchronized long currentSessionStart(long gameTime, int timeoutTicks) {
            if(!isSessionActive(gameTime, timeoutTicks)) return gameTime + 1;
            return gameTimes[temporal(timeoutTicks).currentStartIndex];
        }

        public float realtimeDps(long gameTime, int windowTicks) {
            return windowDps(actualPrefix, gameTime, windowTicks, 0);
        }

        public float realtimeOriginalDps(long gameTime, int windowTicks) {
            return windowDps(originalPrefix, gameTime, windowTicks, 0);
        }

        public synchronized float currentSessionDps(long gameTime, int windowTicks, int timeoutTicks) {
            TemporalResult temporal = temporal(timeoutTicks);
            if(!isSessionActive(gameTime, timeoutTicks)) return 0;
            return windowDps(actualPrefix, gameTime, windowTicks, temporal.currentStartIndex);
        }

        public synchronized float currentSessionOriginalDps(long gameTime, int windowTicks, int timeoutTicks) {
            TemporalResult temporal = temporal(timeoutTicks);
            if(!isSessionActive(gameTime, timeoutTicks)) return 0;
            return windowDps(originalPrefix, gameTime, windowTicks, temporal.currentStartIndex);
        }

        public synchronized List<SessionAggregate> finishedSessions(long gameTime, int timeoutTicks, int limit) {
            if(limit <= 0) return List.of();
            List<SessionAggregate> finished = temporal(timeoutTicks).sessions.stream()
                    .filter(session -> gameTime - session.lastGameTime() > timeoutTicks)
                    .toList();
            int start = Math.max(0, finished.size() - limit);
            return List.copyOf(finished.subList(start, finished.size()));
        }

        public synchronized Map<UUID, DamageContribution> contributions(ContributionDimension dimension) {
            Map<UUID, DamageContribution> cached = contributions.get(dimension);
            if(cached != null) return cached;
            Map<UUID, DamageContribution> result = new HashMap<>();
            for (DamageRecord record : records) {
                UUID id = switch (dimension) {
                    case SOURCE -> record.source().id();
                    case TARGET -> record.target().id();
                    case DIRECT_SOURCE -> record.directSource().id();
                };
                result.merge(id, new DamageContribution(record.actualDamage(), 1), DamageContribution::add);
            }
            Map<UUID, DamageContribution> immutable = Map.copyOf(result);
            contributions.put(dimension, immutable);
            return immutable;
        }

        private synchronized TemporalResult temporal(int timeoutTicks) {
            TemporalResult cached = temporalResults.get(timeoutTicks);
            if(cached != null) return cached;
            TemporalResult result = buildTemporal(timeoutTicks);
            temporalResults.put(timeoutTicks, result);
            return result;
        }

        private TemporalResult buildTemporal(int timeoutTicks) {
            if(records.isEmpty()) {
                return new TemporalResult(-1,
                        new DamageAccumulator(DamageAccumulator.OpponentGrouping.INSTANCE),
                        new DamageAccumulator(DamageAccumulator.OpponentGrouping.INSTANCE), List.of());
            }
            List<SessionAggregate> sessions = new ArrayList<>();
            int sessionStart = 0;
            for (int index = 1; index < records.size(); index++) {
                if(gameTimes[index] - gameTimes[index - 1] <= timeoutTicks) continue;
                sessions.add(sessionAggregate(sessionStart, index));
                sessionStart = index;
            }
            sessions.add(sessionAggregate(sessionStart, records.size()));

            DamageAccumulator outgoing = new DamageAccumulator(DamageAccumulator.OpponentGrouping.INSTANCE);
            DamageAccumulator incoming = new DamageAccumulator(DamageAccumulator.OpponentGrouping.INSTANCE);
            for (int index = sessionStart; index < records.size(); index++) {
                DamageRecord record = records.get(index);
                outgoing.accept(record, record.target());
                incoming.accept(record, record.source());
            }
            return new TemporalResult(sessionStart, outgoing, incoming, List.copyOf(sessions));
        }

        private SessionAggregate sessionAggregate(int start, int end) {
            DamageAccumulator accumulator = new DamageAccumulator(DamageAccumulator.OpponentGrouping.NONE);
            for (int index = start; index < end; index++) {
                DamageRecord record = records.get(index);
                accumulator.accept(record, record.target());
            }
            return new SessionAggregate(accumulator.getTotalActual(), accumulator.getAverageDps(),
                    accumulator.getDurationTicks(), accumulator.getHitCount(), gameTimes[end - 1]);
        }

        private float windowDps(double[] prefix, long gameTime, int windowTicks, int minimumIndex) {
            long cutoff = gameTime - windowTicks;
            int start = Math.max(minimumIndex, lowerBound(gameTimes, cutoff));
            int end = upperBound(gameTimes, gameTime);
            if(start >= end) return 0;
            double sum = prefix[end] - prefix[start];
            return (float) (sum / (windowTicks / DamageAccumulator.TICKS_PER_SECOND));
        }

        private static int lowerBound(long[] values, long target) {
            int low = 0;
            int high = values.length;
            while(low < high) {
                int middle = (low + high) >>> 1;
                if(values[middle] < target) low = middle + 1;
                else high = middle;
            }
            return low;
        }

        private static int upperBound(long[] values, long target) {
            int low = 0;
            int high = values.length;
            while(low < high) {
                int middle = (low + high) >>> 1;
                if(values[middle] <= target) low = middle + 1;
                else high = middle;
            }
            return low;
        }

        private static final class TemporalResult {
            private final int currentStartIndex;
            private final DamageAccumulator outgoingSession;
            private final DamageAccumulator incomingSession;
            private final List<SessionAggregate> sessions;

            private TemporalResult(int currentStartIndex, DamageAccumulator outgoingSession,
                                   DamageAccumulator incomingSession, List<SessionAggregate> sessions) {
                this.currentStartIndex = currentStartIndex;
                this.outgoingSession = outgoingSession;
                this.incomingSession = incomingSession;
                this.sessions = sessions;
            }
        }
    }

    private record SegmentMetadata(
            long id,
            long firstSequence,
            long lastSequence,
            int recordCount,
            Set<UUID> sourceIds,
            Set<ResourceLocation> sourceTypes,
            Set<UUID> targetIds,
            Set<ResourceLocation> targetTypes,
            Set<UUID> directSourceIds,
            Set<ResourceLocation> directSourceTypes,
            Set<ResourceLocation> damageTypes
    ) {
        private boolean mayMatch(StatsFilter filter) {
            return mayMatch(filter.source(), sourceIds, sourceTypes)
                    && mayMatch(filter.target(), targetIds, targetTypes)
                    && mayMatch(filter.directSource(), directSourceIds, directSourceTypes)
                    && mayMatch(filter.damageType());
        }

        private boolean mayMatch(Optional<EntitySelector> selector, Set<UUID> ids, Set<ResourceLocation> types) {
            if(selector.isEmpty()) return true;
            return switch (selector.get()) {
                case EntitySelector.Instance(EntityRef ref) -> ids.contains(ref.id());
                case EntitySelector.Type(ResourceLocation typeId) -> types.contains(typeId);
            };
        }

        /** 分类由可热重载 JSON 决定，索引不能安全地假定分类内容，因此只优化精确 ID。 */
        private boolean mayMatch(Optional<DamageTypeSelector> selector) {
            if(selector.isEmpty()) return true;
            return switch (selector.get()) {
                case DamageTypeSelector.Category ignored -> true;
                case DamageTypeSelector.Exact(ResourceLocation id) -> damageTypes.contains(id);
            };
        }
    }

    private static final class SegmentBuilder {
        private final long id;
        private long firstSequence = -1;
        private long lastSequence = -1;
        private int recordCount;
        private final Set<UUID> sourceIds = new HashSet<>();
        private final Set<ResourceLocation> sourceTypes = new HashSet<>();
        private final Set<UUID> targetIds = new HashSet<>();
        private final Set<ResourceLocation> targetTypes = new HashSet<>();
        private final Set<UUID> directSourceIds = new HashSet<>();
        private final Set<ResourceLocation> directSourceTypes = new HashSet<>();
        private final Set<ResourceLocation> damageTypes = new HashSet<>();

        private SegmentBuilder(long id) {
            this.id = id;
        }

        private void accept(JournalEntry entry) {
            DamageRecord record = entry.record();
            if(recordCount == 0) firstSequence = entry.sequence();
            lastSequence = entry.sequence();
            recordCount++;
            add(record.source(), sourceIds, sourceTypes);
            add(record.target(), targetIds, targetTypes);
            add(record.directSource(), directSourceIds, directSourceTypes);
            damageTypes.add(record.damageTypeId());
        }

        private boolean isEmpty() {
            return recordCount == 0;
        }

        private boolean isFull() {
            return recordCount >= SEGMENT_RECORD_LIMIT;
        }

        private SegmentMetadata build() {
            return new SegmentMetadata(
                    id,
                    firstSequence,
                    lastSequence,
                    recordCount,
                    Set.copyOf(sourceIds),
                    Set.copyOf(sourceTypes),
                    Set.copyOf(targetIds),
                    Set.copyOf(targetTypes),
                    Set.copyOf(directSourceIds),
                    Set.copyOf(directSourceTypes),
                    Set.copyOf(damageTypes)
            );
        }

        private static void add(EntityRef ref, Set<UUID> ids, Set<ResourceLocation> types) {
            ids.add(ref.id());
            types.add(ref.typeIdOrEnvironment());
        }
    }

    private final Path directory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Deque<JournalEntry> pending = new ArrayDeque<>();
    private final List<SegmentMetadata> sealedSegments = new ArrayList<>();
    private final Map<UUID, Long> resetSequences = new HashMap<>();
    private final List<JournalEntry> queryEntries = new ArrayList<>();
    private final Map<UUID, List<JournalEntry>> sourceIdIndex = new HashMap<>();
    private final Map<ResourceLocation, List<JournalEntry>> sourceTypeIndex = new HashMap<>();
    private final Map<UUID, List<JournalEntry>> targetIdIndex = new HashMap<>();
    private final Map<ResourceLocation, List<JournalEntry>> targetTypeIndex = new HashMap<>();
    private final Map<UUID, List<JournalEntry>> directSourceIdIndex = new HashMap<>();
    private final Map<ResourceLocation, List<JournalEntry>> directSourceTypeIndex = new HashMap<>();
    private final Map<ResourceLocation, List<JournalEntry>> damageTypeIndex = new HashMap<>();
    private final Map<String, List<JournalEntry>> damageCategoryIndex = new HashMap<>();
    private final LinkedHashMap<StatsFilter, QueryResult> queryCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<StatsFilter, QueryResult> eldest) {
            return size() > 8;
        }
    };
    private boolean queryIndexLoaded;
    private SegmentBuilder activeSegment;
    private long nextSequence;
    private long eventCount;
    private long queryRevision;
    private boolean closed;
    private boolean clearing;
    private StorageIndexState indexState = StorageIndexState.EMPTY;

    private DamageEventJournal(Path directory) {
        this.directory = directory;
        activeSegment = new SegmentBuilder(0);
    }

    public static DamageEventJournal open(Path statsDirectory) {
        DamageEventJournal journal = new DamageEventJournal(statsDirectory.resolve(DIRECTORY_NAME));
        journal.initialize();
        journal.rebuildQueryIndex();
        return journal;
    }

    public void append(DamageRecord record) {
        lock.writeLock().lock();
        try {
            if(closed || clearing) return;
            JournalEntry entry = new JournalEntry(nextSequence++, record);
            pending.addLast(entry);
            eventCount++;
            invalidateQueryCaches();
            if(queryIndexLoaded) indexEntry(entry);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 每次批量落盘最多重写一个段索引，原始事件帧则直接追加到活跃段。 */
    public boolean flush() {
        lock.writeLock().lock();
        try {
            if(clearing) return false;
            if(closed) return true;
            if(pending.isEmpty()) return retryFailedIndex();
            try {
                Files.createDirectories(directory);
                while(!pending.isEmpty()) {
                    if(activeSegment.isFull() && !sealActiveSegment()) return false;
                    writePendingToActiveSegment();
                }
                return retryFailedIndex();
            } catch (IOException | RuntimeException e) {
                LOGGER.error("写出原始伤害事件段失败：{}", segmentPath(activeSegment.id), e);
                return false;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 正常关闭时封存未满段并写入索引；异常关闭的活跃段会在下次启动时自动恢复。 */
    public boolean close() {
        lock.writeLock().lock();
        try {
            if(closed) return true;
            if(clearing) return false;
            if(!flush() || !pending.isEmpty()) {
                LOGGER.error("正常关闭时原始伤害事件仍有待写记录，保留日志以便重试：{}", pending.size());
                return false;
            }
            if(!activeSegment.isEmpty() && !sealActiveSegment()) {
                if(!activeSegment.isEmpty() || !retryFailedIndex()) {
                    LOGGER.error("正常关闭时原始伤害事件段封存失败，日志未标记为关闭：{}", directory);
                    return false;
                }
            }
            if(!retryFailedIndex()) {
                LOGGER.error("正常关闭时原始伤害事件索引写入失败，日志未标记为关闭：{}", directory);
                return false;
            }
            closed = true;
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 服务端查询只读取可能命中的段，最终仍逐条匹配，索引误命中不会影响准确性。 */
    public void forEachMatching(StatsFilter filter, Consumer<DamageRecord> consumer) {
        matching(filter).forEach(consumer);
    }

    public List<DamageRecord> matching(StatsFilter filter) {
        return query(filter).records();
    }

    /** 按筛选条件复用同一份事件、聚合和时间索引快照。 */
    public QueryResult query(StatsFilter filter) {
        lock.readLock().lock();
        try {
            QueryResult cached;
            synchronized (queryCache) {
                cached = queryCache.get(filter);
            }
            if(cached != null) return cached;
            List<JournalEntry> matched = new ArrayList<>();
            for (JournalEntry entry : candidateEntries(filter)) {
                if(visibleToFilter(entry, filter) && filter.matches(entry.record())) matched.add(entry);
            }
            QueryResult result = new QueryResult(matched);
            synchronized (queryCache) {
                queryCache.put(filter, result);
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 聚合缓存不可用时，从权威事件日志重建可快速查询的运行时数据。 */
    public DamageTracker rebuildTracker(long currentGameTime) {
        lock.readLock().lock();
        try {
            List<DamageTracker.ReplayRecord> records = queryEntries.stream()
                    .map(entry -> new DamageTracker.ReplayRecord(entry.sequence(), entry.record()))
                    .toList();
            return DamageTracker.rebuildFromSequencedRecords(records, resetSequences, currentGameTime);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Map<UUID, DamageContribution> contributions(StatsFilter filter, ContributionDimension dimension) {
        return query(filter).contributions(dimension);
    }

    public Set<ResourceLocation> recordedSourceTypes() {
        lock.readLock().lock();
        try {
            return recordedTypes(SegmentMetadata::sourceTypes, record -> record.source().typeIdOrEnvironment());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<ResourceLocation> recordedSourceTypesIncludingDirect() {
        lock.readLock().lock();
        try {
            Set<ResourceLocation> types = new HashSet<>(recordedSourceTypes());
            types.addAll(recordedDirectSourceTypes());
            return Set.copyOf(types);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<ResourceLocation> recordedTargetTypes() {
        lock.readLock().lock();
        try {
            return recordedTypes(SegmentMetadata::targetTypes, record -> record.target().typeIdOrEnvironment());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<ResourceLocation> recordedDirectSourceTypes() {
        lock.readLock().lock();
        try {
            return recordedTypes(SegmentMetadata::directSourceTypes, record -> record.directSource().typeIdOrEnvironment());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<ResourceLocation> recordedDamageTypes() {
        lock.readLock().lock();
        try {
            return recordedTypes(SegmentMetadata::damageTypes, record -> record.damageTypeId());
        } finally {
            lock.readLock().unlock();
        }
    }

    private Set<ResourceLocation> recordedTypes(Function<SegmentMetadata, Set<ResourceLocation>> sealedTypes,
                                                Function<DamageRecord, ResourceLocation> pendingType) {
        Set<ResourceLocation> types = new HashSet<>();
        sealedSegments.forEach(metadata -> types.addAll(sealedTypes.apply(metadata)));
        if(!activeSegment.isEmpty()) types.addAll(sealedTypes.apply(activeSegment.build()));
        pending.forEach(entry -> types.add(pendingType.apply(entry.record())));
        return Set.copyOf(types);
    }

    /**
     * 个人清空不删除共享的完整事件历史，只记录该实体的可见性边界。
     * 这样它仍不会从其他实体的查询结果中消失，和旧版单实例重置的语义一致。
     */
    public boolean markReset(EntityRef owner) {
        lock.writeLock().lock();
        try {
            Long previous = resetSequences.put(owner.id(), nextSequence);
            if(writeResetSequences()) {
                invalidateQueryCaches();
                return true;
            }
            if(previous == null) resetSequences.remove(owner.id());
            else resetSequences.put(owner.id(), previous);
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 组合筛选没有可复用的预聚合会话时，按完整事件历史切分当前一场战斗。 */
    public long currentSessionStart(StatsFilter filter, long gameTime, int timeoutTicks) {
        return query(filter).currentSessionStart(gameTime, timeoutTicks);
    }

    /** 开始跨文件清理事务；事务完成前拒绝新的事件追加。 */
    public boolean beginClear() {
        lock.writeLock().lock();
        try {
            if(closed || clearing) return false;
            clearing = true;
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 存储层提交清理事务后，丢弃内存中的事件、索引和查询缓存。 */
    public void completeClear() {
        lock.writeLock().lock();
        try {
            pending.clear();
            sealedSegments.clear();
            resetSequences.clear();
            clearQueryIndex();
            queryIndexLoaded = true;
            activeSegment = new SegmentBuilder(0);
            nextSequence = 0;
            eventCount = 0;
            queryRevision++;
            closed = false;
            indexState = StorageIndexState.EMPTY;
            clearing = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 清理事务失败时恢复追加能力，并保留尚未提交的内存数据。 */
    public void abortClear() {
        lock.writeLock().lock();
        try {
            clearing = false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** 已提交磁盘清理但内存状态尚未清空时，调用方不得把旧聚合或事件再次写回磁盘。 */
    public boolean isClearing() {
        lock.readLock().lock();
        try {
            return clearing;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void invalidateQueryCaches() {
        lock.writeLock().lock();
        try {
            queryRevision++;
            synchronized (queryCache) {
                queryCache.clear();
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void invalidateDamageTypeCategoryCache() {
        lock.writeLock().lock();
        try {
            if(queryIndexLoaded) rebuildDamageCategoryIndex();
            invalidateQueryCaches();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public long queryRevision() {
        lock.readLock().lock();
        try {
            return queryRevision;
        } finally {
            lock.readLock().unlock();
        }
    }

    private synchronized void rebuildQueryIndex() {
        clearQueryIndex();
        List<JournalEntry> restored = new ArrayList<>();
        sealedSegments.stream()
                .sorted(Comparator.comparingLong(SegmentMetadata::firstSequence))
                .forEach(metadata -> readEntries(segmentPath(metadata.id), restored::add));
        if(!activeSegment.isEmpty()) readEntries(segmentPath(activeSegment.id), restored::add);
        restored.addAll(pending);
        restored.sort(Comparator.comparingLong(JournalEntry::sequence));
        restored.forEach(this::indexEntry);
        queryIndexLoaded = true;
    }

    private void clearQueryIndex() {
        queryEntries.clear();
        sourceIdIndex.clear();
        sourceTypeIndex.clear();
        targetIdIndex.clear();
        targetTypeIndex.clear();
        directSourceIdIndex.clear();
        directSourceTypeIndex.clear();
        damageTypeIndex.clear();
        damageCategoryIndex.clear();
        synchronized (queryCache) {
            queryCache.clear();
        }
        queryIndexLoaded = false;
    }

    private void indexEntry(JournalEntry entry) {
        queryEntries.add(entry);
        DamageRecord record = entry.record();
        addIndex(sourceIdIndex, record.source().id(), entry);
        addIndex(sourceTypeIndex, record.source().typeIdOrEnvironment(), entry);
        addIndex(targetIdIndex, record.target().id(), entry);
        addIndex(targetTypeIndex, record.target().typeIdOrEnvironment(), entry);
        addIndex(directSourceIdIndex, record.directSource().id(), entry);
        addIndex(directSourceTypeIndex, record.directSource().typeIdOrEnvironment(), entry);
        addIndex(damageTypeIndex, record.damageTypeId(), entry);
        String category = DamageTypeCategories.categoryOf(record.damageTypeId());
        if(category != null) addIndex(damageCategoryIndex, category, entry);
    }

    private void rebuildDamageCategoryIndex() {
        damageCategoryIndex.clear();
        for (JournalEntry entry : queryEntries) {
            String category = DamageTypeCategories.categoryOf(entry.record().damageTypeId());
            if(category != null) addIndex(damageCategoryIndex, category, entry);
        }
    }

    private static <K> void addIndex(Map<K, List<JournalEntry>> index, K key, JournalEntry entry) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entry);
    }

    private List<JournalEntry> candidateEntries(StatsFilter filter) {
        List<List<JournalEntry>> candidates = new ArrayList<>(5);
        if(filter.sourceIsDirectSource()) {
            addCandidates(candidates, filter.source(), directSourceIdIndex, directSourceTypeIndex);
        } else {
            addCandidates(candidates, filter.source(), sourceIdIndex, sourceTypeIndex);
        }
        addCandidates(candidates, filter.target(), targetIdIndex, targetTypeIndex);
        addCandidates(candidates, filter.directSource(), directSourceIdIndex, directSourceTypeIndex);
        filter.damageType().ifPresent(selector -> {
            switch (selector) {
                case DamageTypeSelector.Category category -> candidates.add(
                        damageCategoryIndex.getOrDefault(category.name(), List.of()));
                case DamageTypeSelector.Exact exact -> candidates.add(
                        damageTypeIndex.getOrDefault(exact.id(), List.of()));
            }
        });
        return candidates.stream()
                .min(Comparator.comparingInt(List::size))
                .orElse(queryEntries);
    }

    private static void addCandidates(List<List<JournalEntry>> candidates,
                                      Optional<EntitySelector> selector,
                                      Map<UUID, List<JournalEntry>> instanceIndex,
                                      Map<ResourceLocation, List<JournalEntry>> typeIndex) {
        if(selector.isEmpty()) return;
        candidates.add(switch (selector.get()) {
            case EntitySelector.Instance(EntityRef ref) ->
                    instanceIndex.getOrDefault(ref.id(), List.of());
            case EntitySelector.Type(ResourceLocation typeId) ->
                    typeIndex.getOrDefault(typeId, List.of());
        });
    }

    private void initialize() {
        try {
            Files.createDirectories(directory);
            readResetSequences();
            NavigableMap<Long, Path> segmentPaths = findSegmentPaths();
            if(segmentPaths.isEmpty() && migrateLegacySegments()) {
                indexState = StorageIndexState.REBUILT;
                return;
            }
            if(segmentPaths.isEmpty()) {
                activeSegment = new SegmentBuilder(0);
                indexState = StorageIndexState.EMPTY;
                return;
            }
            List<SegmentMetadata> loadedIndex = readIndex();
            if(loadedIndex == null || !validIndex(loadedIndex, segmentPaths)) {
                rebuildIndex(segmentPaths);
                return;
            }
            restoreFromIndex(loadedIndex, segmentPaths);
            indexState = StorageIndexState.LOADED;
        } catch (IOException e) {
            LOGGER.error("初始化原始伤害事件存储失败，将以空事件历史继续运行", e);
            sealedSegments.clear();
            activeSegment = new SegmentBuilder(0);
            nextSequence = 0;
            eventCount = 0;
            indexState = StorageIndexState.FAILED;
        }
    }

    /** 索引无法通过校验时扫描全部段重建；任何读不出的段都会保留在磁盘上并记录错误。 */
    private void rebuildIndex(NavigableMap<Long, Path> segmentPaths) {
        sealedSegments.clear();
        long highestSequence = -1;
        long activeId = segmentPaths.isEmpty() || isCompressedSegment(segmentPaths.lastEntry().getValue())
                ? -1 : segmentPaths.lastKey();
        SegmentBuilder rebuiltActive = null;
        for (Map.Entry<Long, Path> entry : segmentPaths.entrySet()) {
            SegmentBuilder builder = scanSegment(entry.getKey(), entry.getValue());
            if(entry.getKey() == activeId) {
                rebuiltActive = builder;
                if(!builder.isEmpty()) highestSequence = Math.max(highestSequence, builder.lastSequence);
                continue;
            }
            if(builder.isEmpty()) continue;
            if(!isCompressedSegment(entry.getValue())) compressSegment(entry.getKey());
            SegmentMetadata metadata = builder.build();
            sealedSegments.add(metadata);
            highestSequence = Math.max(highestSequence, metadata.lastSequence());
        }
        sealedSegments.sort(Comparator.comparingLong(SegmentMetadata::id));
        nextSequence = highestSequence + 1;
        eventCount = sealedSegments.stream().mapToLong(SegmentMetadata::recordCount).sum()
                + (rebuiltActive == null ? 0 : rebuiltActive.recordCount);
        activeSegment = rebuiltActive != null
                ? rebuiltActive
                : new SegmentBuilder(segmentPaths.isEmpty() ? 0 : segmentPaths.lastKey() + 1);
        indexState = writeIndex() ? StorageIndexState.REBUILT : StorageIndexState.FAILED;
    }

    private void restoreFromIndex(List<SegmentMetadata> loadedIndex, NavigableMap<Long, Path> segmentPaths) {
        sealedSegments.clear();
        sealedSegments.addAll(loadedIndex);
        Set<Long> indexedIds = new HashSet<>();
        loadedIndex.forEach(metadata -> indexedIds.add(metadata.id()));
        List<Long> unindexedIds = segmentPaths.keySet().stream().filter(id -> !indexedIds.contains(id)).toList();
        long highestSequence = loadedIndex.stream().mapToLong(SegmentMetadata::lastSequence).max().orElse(-1);
        if(unindexedIds.isEmpty()) {
            activeSegment = new SegmentBuilder(segmentPaths.lastKey() + 1);
            nextSequence = highestSequence + 1;
            eventCount = loadedIndex.stream().mapToLong(SegmentMetadata::recordCount).sum();
            return;
        }

        long activeId = unindexedIds.stream()
                .filter(id -> !isCompressedSegment(segmentPaths.get(id)))
                .max(Long::compareTo)
                .orElse(-1L);
        for (long id : unindexedIds) {
            SegmentBuilder builder = scanSegment(id, segmentPaths.get(id));
            if(builder.isEmpty()) continue;
            highestSequence = Math.max(highestSequence, builder.build().lastSequence());
            if(id == activeId) {
                activeSegment = builder;
            } else sealedSegments.add(builder.build());
        }
        if(activeId < 0) activeSegment = new SegmentBuilder(segmentPaths.lastKey() + 1);
        else if(activeSegment.id != activeId) activeSegment = new SegmentBuilder(activeId);
        sealedSegments.sort(Comparator.comparingLong(SegmentMetadata::id));
        nextSequence = highestSequence + 1;
        eventCount = sealedSegments.stream().mapToLong(SegmentMetadata::recordCount).sum()
                + (activeSegment == null ? 0 : activeSegment.recordCount);
        if(writeIndex()) indexState = StorageIndexState.LOADED;
    }

    private boolean migrateLegacySegments() {
        List<Path> legacyPaths;
        try (Stream<Path> files = Files.list(directory)) {
            legacyPaths = files.filter(DamageEventJournal::isLegacySegment).sorted().toList();
        } catch (IOException e) {
            LOGGER.error("查找旧版原始伤害事件段失败：{}", directory, e);
            return false;
        }
        if(legacyPaths.isEmpty()) return false;

        List<LegacyJournalEntry> entries = new ArrayList<>();
        for (Path legacyPath : legacyPaths) {
            if(!readLegacyEntries(legacyPath, entries)) return false;
        }
        entries.sort(Comparator.comparingLong(LegacyJournalEntry::sequence));
        for (LegacyJournalEntry entry : entries) append(entry.record());
        if(!flush() || !pending.isEmpty()) return false;
        if(!activeSegment.isEmpty() && !sealActiveSegment()) {
            if(!activeSegment.isEmpty() || !retryFailedIndex()) return false;
        }
        if(!retryFailedIndex()) return false;
        for (Path legacyPath : legacyPaths) {
            Path migrated = legacyPath.resolveSibling(legacyPath.getFileName() + ".migrated");
            try {
                Files.move(legacyPath, migrated, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                LOGGER.warn("原始伤害事件已迁移，但无法保留旧段副本：{}", legacyPath, e);
            }
        }
        return true;
    }

    private boolean readLegacyEntries(Path legacyPath, List<LegacyJournalEntry> entries) {
        int lineNumber = 0;
        int importedCount = 0;
        int skippedCount = 0;
        try (BufferedReader reader = Files.newBufferedReader(legacyPath, StandardCharsets.UTF_8)) {
            String line;
            while((line = reader.readLine()) != null) {
                lineNumber++;
                if(line.isBlank()) continue;
                int currentLine = lineNumber;
                try {
                    Optional<LegacyJournalEntry> parsed = LegacyJournalEntry.CODEC
                            .parse(JsonOps.INSTANCE, JsonParser.parseString(line))
                            .resultOrPartial(error -> LOGGER.warn(
                                    "旧版原始伤害事件第 {} 行解析失败，将跳过：{}，文件：{}",
                                    currentLine, error, legacyPath));
                    if(parsed.isPresent()) {
                        entries.add(parsed.get());
                        importedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (RuntimeException e) {
                    skippedCount++;
                    LOGGER.warn("旧版原始伤害事件第 {} 行不是有效 JSON，将跳过，文件：{}",
                            currentLine, legacyPath, e);
                }
            }
            if(skippedCount > 0) LOGGER.warn(
                    "旧版原始伤害事件已按行迁移：导入 {} 行，跳过 {} 行损坏数据，原文件会保留为迁移副本：{}",
                    importedCount, skippedCount, legacyPath);
            return true;
        } catch (IOException e) {
            LOGGER.error("读取旧版原始伤害事件段失败，已保留原文件：{}", legacyPath, e);
            return false;
        }
    }

    private void writePendingToActiveSegment() throws IOException {
        Path path = segmentPath(activeSegment.id);
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            if(channel.size() == 0) writeSegmentHeader(channel);
            while(!pending.isEmpty() && !activeSegment.isFull()) {
                JournalEntry entry = pending.getFirst();
                byte[] frame = encodeFrame(entry);
                long frameStart = channel.position();
                try {
                    writeFully(channel, ByteBuffer.wrap(frame));
                } catch (IOException e) {
                    channel.truncate(frameStart);
                    throw e;
                }
                activeSegment.accept(entry);
                pending.removeFirst();
            }
            channel.force(false);
        }
    }

    private boolean sealActiveSegment() {
        if(activeSegment.isEmpty()) return true;
        if(!compressSegment(activeSegment.id)) return false;
        sealedSegments.add(activeSegment.build());
        sealedSegments.sort(Comparator.comparingLong(SegmentMetadata::id));
        activeSegment = new SegmentBuilder(activeSegment.id + 1);
        boolean indexWritten = writeIndex();
        indexState = indexWritten ? StorageIndexState.LOADED : StorageIndexState.FAILED;
        return indexWritten;
    }

    private boolean retryFailedIndex() {
        if(indexState != StorageIndexState.FAILED) return true;
        boolean indexWritten = writeIndex();
        if(indexWritten) indexState = StorageIndexState.LOADED;
        return indexWritten;
    }

    /** 活跃段不压缩，避免每次批量写入都重写整段；封存后再压缩可获得跨事件字段的压缩率。 */
    private boolean compressSegment(long id) {
        Path source = rawSegmentPath(id);
        if(!Files.isRegularFile(source)) return Files.isRegularFile(compressedSegmentPath(id));
        Path target = compressedSegmentPath(id);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try (InputStream input = new BufferedInputStream(Files.newInputStream(source));
             OutputStream output = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
            input.transferTo(output);
        } catch (IOException e) {
            LOGGER.error("压缩原始伤害事件段失败：{}", source, e);
            deleteFile(temporary);
            return false;
        }
        try {
            moveIntoPlace(temporary, target);
            Files.deleteIfExists(source);
            return true;
        } catch (IOException e) {
            LOGGER.error("封存原始伤害事件压缩段失败：{}", target, e);
            deleteFile(temporary);
            return false;
        }
    }

    private SegmentBuilder scanSegment(long id, Path path) {
        SegmentBuilder builder = new SegmentBuilder(id);
        readEntries(path, builder::accept);
        return builder;
    }

    private void readRecords(Path path, StatsFilter filter, Consumer<DamageRecord> consumer) {
        readEntries(path, entry -> {
            if(visibleToFilter(entry, filter) && filter.matches(entry.record())) consumer.accept(entry.record());
        });
    }

    private boolean visibleToFilter(JournalEntry entry, StatsFilter filter) {
        DamageRecord record = entry.record();
        return !clearedBefore(entry, filter.source(), record.source())
                && !clearedBefore(entry, filter.target(), record.target())
                && !clearedBefore(entry, filter.directSource(), record.directSource());
    }

    private boolean clearedBefore(JournalEntry entry, Optional<EntitySelector> selector, EntityRef candidate) {
        if(!(selector.orElse(null) instanceof EntitySelector.Instance instance)) return false;
        if(!instance.ref().id().equals(candidate.id())) return false;
        Long resetSequence = resetSequences.get(candidate.id());
        return resetSequence != null && entry.sequence() < resetSequence;
    }

    private void readEntries(Path path, Consumer<JournalEntry> consumer) {
        if(isCompressedSegment(path)) {
            readCompressedEntries(path, consumer);
            return;
        }
        readRawEntries(path, consumer);
    }

    private void readCompressedEntries(Path path, Consumer<JournalEntry> consumer) {
        try (InputStream raw = new BufferedInputStream(Files.newInputStream(path));
             InputStream source = new GZIPInputStream(raw);
             DataInputStream input = new DataInputStream(source)) {
            if(input.readInt() != SEGMENT_MAGIC) {
                LOGGER.error("原始伤害事件段格式不正确，已跳过：{}", path);
                return;
            }
            if(input.readInt() != FORMAT_VERSION) {
                LOGGER.error("原始伤害事件段版本不受支持，已跳过：{}", path);
                return;
            }
            while(true) {
                int length;
                try {
                    length = input.readInt();
                } catch (EOFException ignored) {
                    return;
                }
                if(length <= 0 || length > MAX_FRAME_SIZE) {
                    LOGGER.error("原始伤害事件段存在非法帧长度，已忽略其后的内容：{}", path);
                    return;
                }
                try {
                    int expectedChecksum = input.readInt();
                    byte[] payload = input.readNBytes(length);
                    if(payload.length != length) {
                        LOGGER.warn("原始伤害事件段尾部不完整，已忽略最后一帧：{}", path);
                        return;
                    }
                    if(expectedChecksum != checksum(payload)) {
                        LOGGER.warn("原始伤害事件段尾部校验失败，已忽略最后一帧及其后内容：{}", path);
                        return;
                    }
                    consumer.accept(decodeEntry(payload));
                } catch (EOFException ignored) {
                    LOGGER.warn("原始伤害事件段尾部不完整，已忽略最后一帧：{}", path);
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("读取原始伤害事件段失败：{}", path, e);
        }
    }

    /** 原始活跃段可直接定位尾部，发现半帧或校验错误时必须截断后再允许追加。 */
    private void readRawEntries(Path path, Consumer<JournalEntry> consumer) {
        try (RandomAccessFile input = new RandomAccessFile(path.toFile(), "rw")) {
            long fileSize = input.length();
            if(fileSize < SEGMENT_HEADER_SIZE) {
                repairRawHeader(input, path);
                return;
            }
            if(input.readInt() != SEGMENT_MAGIC || input.readInt() != FORMAT_VERSION) {
                repairRawHeader(input, path);
                return;
            }

            long lastGoodPosition = SEGMENT_HEADER_SIZE;
            while(input.getFilePointer() < fileSize) {
                long frameStart = input.getFilePointer();
                if(fileSize - frameStart < Integer.BYTES) {
                    truncateRawSegment(input, path, lastGoodPosition);
                    return;
                }
                int length = input.readInt();
                if(length <= 0 || length > MAX_FRAME_SIZE
                        || fileSize - input.getFilePointer() < Integer.BYTES + (long) length) {
                    LOGGER.warn("原始伤害事件段存在不完整或非法帧，已截断尾部：{}", path);
                    truncateRawSegment(input, path, lastGoodPosition);
                    return;
                }
                int expectedChecksum = input.readInt();
                byte[] payload = new byte[length];
                input.readFully(payload);
                if(expectedChecksum != checksum(payload)) {
                    LOGGER.warn("原始伤害事件段尾部校验失败，已截断错误帧及其后内容：{}", path);
                    truncateRawSegment(input, path, lastGoodPosition);
                    return;
                }
                try {
                    consumer.accept(decodeEntry(payload));
                } catch (IOException | RuntimeException e) {
                    LOGGER.warn("原始伤害事件段存在无法解码的帧，已截断尾部：{}", path, e);
                    truncateRawSegment(input, path, lastGoodPosition);
                    return;
                }
                lastGoodPosition = input.getFilePointer();
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("读取原始伤害事件段失败：{}", path, e);
        }
    }

    private void repairRawHeader(RandomAccessFile input, Path path) throws IOException {
        LOGGER.error("原始伤害事件段头部无效，已重建为空段：{}", path);
        input.setLength(0);
        input.seek(0);
        input.writeInt(SEGMENT_MAGIC);
        input.writeInt(FORMAT_VERSION);
    }

    private void truncateRawSegment(RandomAccessFile input, Path path, long position) throws IOException {
        input.setLength(position);
        LOGGER.info("原始伤害事件段已恢复到最后一条完整帧：{}，位置 {}", path, position);
    }

    private List<SegmentMetadata> readIndex() {
        Path indexPath = directory.resolve(INDEX_FILE_NAME);
        if(!Files.isRegularFile(indexPath)) return null;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(indexPath)))) {
            if(input.readInt() != INDEX_MAGIC || input.readInt() != FORMAT_VERSION) return null;
            int count = input.readInt();
            if(count < 0 || count > 1_000_000) return null;
            List<SegmentMetadata> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(readMetadata(input));
            return entries;
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("原始伤害事件索引损坏，将自动重建：{}", indexPath, e);
            return null;
        }
    }

    private boolean validIndex(List<SegmentMetadata> index, NavigableMap<Long, Path> segmentPaths) {
        Set<Long> ids = new HashSet<>();
        for (SegmentMetadata metadata : index) {
            if(metadata.recordCount() <= 0 || !ids.add(metadata.id())) return false;
            if(!segmentPaths.containsKey(metadata.id())) return false;
            SegmentBuilder actual = scanSegment(metadata.id(), segmentPaths.get(metadata.id()));
            if(actual.isEmpty() || !actual.build().equals(metadata)) return false;
        }
        Set<Long> unindexed = new HashSet<>(segmentPaths.keySet());
        unindexed.removeAll(ids);
        if(unindexed.size() > 1) return false;
        if(unindexed.size() == 1) {
            long id = unindexed.iterator().next();
            if(id != segmentPaths.lastKey() || isCompressedSegment(segmentPaths.get(id))) return false;
        }
        if(!segmentPaths.isEmpty() && segmentPaths.lastKey() == ids.stream().max(Long::compareTo).orElse(Long.MIN_VALUE)
                && !isCompressedSegment(segmentPaths.lastEntry().getValue())) return false;
        return true;
    }

    private boolean writeIndex() {
        Path target = directory.resolve(INDEX_FILE_NAME);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
                output.writeInt(INDEX_MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeInt(sealedSegments.size());
                for (SegmentMetadata metadata : sealedSegments) writeMetadata(output, metadata);
            }
            moveIntoPlace(temporary, target);
            return true;
        } catch (IOException e) {
            LOGGER.error("写出原始伤害事件索引失败：{}", target, e);
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException e) {
                LOGGER.warn("清理原始伤害事件索引临时文件失败：{}", temporary, e);
            }
        }
    }

    public long eventCount() {
        return eventCount;
    }

    public int segmentCount() {
        return sealedSegments.size() + (activeSegment.isEmpty() && pending.isEmpty() ? 0 : 1);
    }

    public StorageIndexState indexState() {
        return indexState;
    }

    private void readResetSequences() {
        Path path = directory.resolve(RESET_FILE_NAME);
        if(!Files.isRegularFile(path)) return;
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if(input.readInt() != INDEX_MAGIC || input.readInt() != FORMAT_VERSION) return;
            int count = input.readInt();
            if(count < 0 || count > 1_000_000) return;
            for (int i = 0; i < count; i++) {
                UUID id = new UUID(input.readLong(), input.readLong());
                resetSequences.put(id, input.readLong());
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("原始伤害事件重置边界损坏，将忽略旧边界：{}", path, e);
            resetSequences.clear();
        }
    }

    private boolean writeResetSequences() {
        Path target = directory.resolve(RESET_FILE_NAME);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(directory);
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temporary,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)))) {
                output.writeInt(INDEX_MAGIC);
                output.writeInt(FORMAT_VERSION);
                output.writeInt(resetSequences.size());
                for (Map.Entry<UUID, Long> entry : resetSequences.entrySet()) {
                    output.writeLong(entry.getKey().getMostSignificantBits());
                    output.writeLong(entry.getKey().getLeastSignificantBits());
                    output.writeLong(entry.getValue());
                }
            }
            moveIntoPlace(temporary, target);
            return true;
        } catch (IOException e) {
            LOGGER.error("写出原始伤害事件重置边界失败：{}", target, e);
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException e) {
                LOGGER.warn("清理原始伤害事件重置边界临时文件失败：{}", temporary, e);
            }
        }
    }

    private NavigableMap<Long, Path> findSegmentPaths() throws IOException {
        NavigableMap<Long, Path> segments = new TreeMap<>();
        try (Stream<Path> files = Files.list(directory)) {
            files.forEach(path -> {
                Matcher matcher = SEGMENT_NAME.matcher(path.getFileName().toString());
                if(matcher.matches()) {
                    putSegmentPath(segments, matcher, path);
                    return;
                }
                matcher = COMPRESSED_SEGMENT_NAME.matcher(path.getFileName().toString());
                if(!matcher.matches()) return;
                putSegmentPath(segments, matcher, path);
            });
        }
        return segments;
    }

    private void putSegmentPath(NavigableMap<Long, Path> segments, Matcher matcher, Path path) {
        try {
            long id = Long.parseLong(matcher.group(1));
            Path existing = segments.get(id);
            if(existing == null || isCompressedSegment(path)) segments.put(id, path);
        } catch (NumberFormatException e) {
            LOGGER.warn("忽略名称不合法的原始伤害事件段：{}", path);
        }
    }

    private boolean isCompressedSegment(Path path) {
        return COMPRESSED_SEGMENT_NAME.matcher(path.getFileName().toString()).matches();
    }

    private static byte[] encodeFrame(JournalEntry entry) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(payload)) {
            writeEntry(output, entry);
        }
        byte[] bytes = payload.toByteArray();
        if(bytes.length > MAX_FRAME_SIZE) throw new IOException("原始伤害事件帧超出最大尺寸");
        ByteArrayOutputStream frame = new ByteArrayOutputStream(bytes.length + Integer.BYTES * 2);
        try (DataOutputStream output = new DataOutputStream(frame)) {
            output.writeInt(bytes.length);
            output.writeInt(checksum(bytes));
            output.write(bytes);
        }
        return frame.toByteArray();
    }

    private static JournalEntry decodeEntry(byte[] payload) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(payload))) {
            long sequence = input.readLong();
            EntityRef source = readEntityRef(input);
            EntityRef directSource = readEntityRef(input);
            EntityRef target = readEntityRef(input);
            ResourceLocation damageType = readLocation(input);
            float original = input.readFloat();
            float actual = input.readFloat();
            float blocked = input.readFloat();
            DamageReduction reduction = new DamageReduction(
                    input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat());
            long gameTime = input.readLong();
            boolean lethal = input.readBoolean();
            ResourceLocation dimension = readLocation(input);
            int blockX = input.readInt();
            int blockY = input.readInt();
            int blockZ = input.readInt();
            long occurredAt = input.readLong();
            String sourceName = input.available() > 0 ? input.readUTF() : "";
            String directSourceName = input.available() > 0 ? input.readUTF() : "";
            String targetName = input.available() > 0 ? input.readUTF() : "";
            return new JournalEntry(sequence, new DamageRecord(
                    source, directSource, target, damageType, original, actual, blocked, reduction,
                    gameTime, lethal, dimension, blockX, blockY, blockZ, occurredAt,
                    new DamageRecord.ParticipantNames(sourceName, directSourceName, targetName)));
        }
    }

    private static void writeEntry(DataOutput output, JournalEntry entry) throws IOException {
        DamageRecord record = entry.record();
        output.writeLong(entry.sequence());
        writeEntityRef(output, record.source());
        writeEntityRef(output, record.directSource());
        writeEntityRef(output, record.target());
        writeLocation(output, record.damageTypeId());
        output.writeFloat(record.originalDamage());
        output.writeFloat(record.actualDamage());
        output.writeFloat(record.blockedDamage());
        output.writeFloat(record.reduction().armor());
        output.writeFloat(record.reduction().enchantments());
        output.writeFloat(record.reduction().mobEffects());
        output.writeFloat(record.reduction().absorption());
        output.writeFloat(record.reduction().innateResistance());
        output.writeFloat(record.reduction().invulnerability());
        output.writeLong(record.gameTime());
        output.writeBoolean(record.lethal());
        writeLocation(output, record.dimensionId());
        output.writeInt(record.blockX());
        output.writeInt(record.blockY());
        output.writeInt(record.blockZ());
        output.writeLong(record.occurredAtMillis());
        output.writeUTF(record.sourceName());
        output.writeUTF(record.directSourceName());
        output.writeUTF(record.targetName());
    }

    private static void writeMetadata(DataOutput output, SegmentMetadata metadata) throws IOException {
        output.writeLong(metadata.id());
        output.writeLong(metadata.firstSequence());
        output.writeLong(metadata.lastSequence());
        output.writeInt(metadata.recordCount());
        writeUuidSet(output, metadata.sourceIds());
        writeLocationSet(output, metadata.sourceTypes());
        writeUuidSet(output, metadata.targetIds());
        writeLocationSet(output, metadata.targetTypes());
        writeUuidSet(output, metadata.directSourceIds());
        writeLocationSet(output, metadata.directSourceTypes());
        writeLocationSet(output, metadata.damageTypes());
    }

    private static SegmentMetadata readMetadata(DataInput input) throws IOException {
        return new SegmentMetadata(
                input.readLong(),
                input.readLong(),
                input.readLong(),
                input.readInt(),
                readUuidSet(input),
                readLocationSet(input),
                readUuidSet(input),
                readLocationSet(input),
                readUuidSet(input),
                readLocationSet(input),
                readLocationSet(input)
        );
    }

    private static void writeEntityRef(DataOutput output, EntityRef ref) throws IOException {
        output.writeLong(ref.id().getMostSignificantBits());
        output.writeLong(ref.id().getLeastSignificantBits());
        ResourceLocation type = ref.typeId();
        output.writeBoolean(type != null);
        if(type != null) writeLocation(output, type);
    }

    private static EntityRef readEntityRef(DataInput input) throws IOException {
        UUID id = new UUID(input.readLong(), input.readLong());
        ResourceLocation type = input.readBoolean() ? readLocation(input) : null;
        return new EntityRef(id, type);
    }

    private static void writeUuidSet(DataOutput output, Set<UUID> ids) throws IOException {
        output.writeInt(ids.size());
        for (UUID id : ids) {
            output.writeLong(id.getMostSignificantBits());
            output.writeLong(id.getLeastSignificantBits());
        }
    }

    private static Set<UUID> readUuidSet(DataInput input) throws IOException {
        int size = readSetSize(input);
        Set<UUID> ids = new HashSet<>(size);
        for (int i = 0; i < size; i++) ids.add(new UUID(input.readLong(), input.readLong()));
        return Set.copyOf(ids);
    }

    private static void writeLocationSet(DataOutput output, Set<ResourceLocation> locations) throws IOException {
        output.writeInt(locations.size());
        for (ResourceLocation location : locations) writeLocation(output, location);
    }

    private static Set<ResourceLocation> readLocationSet(DataInput input) throws IOException {
        int size = readSetSize(input);
        Set<ResourceLocation> locations = new HashSet<>(size);
        for (int i = 0; i < size; i++) locations.add(readLocation(input));
        return Set.copyOf(locations);
    }

    private static int readSetSize(DataInput input) throws IOException {
        int size = input.readInt();
        if(size < 0 || size > SEGMENT_RECORD_LIMIT) throw new IOException("原始伤害事件索引集合尺寸不合法");
        return size;
    }

    private static void writeLocation(DataOutput output, ResourceLocation location) throws IOException {
        output.writeUTF(location.toString());
    }

    private static ResourceLocation readLocation(DataInput input) throws IOException {
        String value = input.readUTF();
        ResourceLocation location = ResourceLocation.tryParse(value);
        if(location == null) throw new IOException("原始伤害事件中存在非法资源位置：" + value);
        return location;
    }

    private static int checksum(byte[] payload) {
        CRC32 checksum = new CRC32();
        checksum.update(payload);
        return (int) checksum.getValue();
    }

    private static void writeSegmentHeader(FileChannel channel) throws IOException {
        ByteBuffer header = ByteBuffer.allocate(Integer.BYTES * 2);
        header.putInt(SEGMENT_MAGIC);
        header.putInt(FORMAT_VERSION);
        header.flip();
        writeFully(channel, header);
    }

    private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
        while(buffer.hasRemaining()) channel.write(buffer);
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static boolean isJournalFile(Path path) {
        String fileName = path.getFileName().toString();
        return SEGMENT_NAME.matcher(fileName).matches()
                || COMPRESSED_SEGMENT_NAME.matcher(fileName).matches()
                || LEGACY_SEGMENT_NAME.matcher(fileName).matches()
                || fileName.equals(INDEX_FILE_NAME)
                || fileName.equals(RESET_FILE_NAME)
                || fileName.endsWith(".tmp");
    }

    private static boolean isLegacySegment(Path path) {
        return LEGACY_SEGMENT_NAME.matcher(path.getFileName().toString()).matches();
    }

    private void deleteFile(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.error("删除原始伤害事件文件失败：{}", path, e);
        }
    }

    private Path segmentPath(long id) {
        Path compressed = compressedSegmentPath(id);
        return Files.isRegularFile(compressed) ? compressed : rawSegmentPath(id);
    }

    private Path rawSegmentPath(long id) {
        return directory.resolve("segment-" + id + ".bin");
    }

    private Path compressedSegmentPath(long id) {
        return directory.resolve("segment-" + id + ".bin.gz");
    }
}
