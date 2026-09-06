package io.zershyan.damagestats.stats.save;

import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.zershyan.damagestats.stats.DamageRecord;
import io.zershyan.damagestats.stats.DamageReduction;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.filter.DamageTypeSelector;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
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
    private final Deque<JournalEntry> pending = new ArrayDeque<>();
    private final List<SegmentMetadata> sealedSegments = new ArrayList<>();
    private final Map<UUID, Long> resetSequences = new HashMap<>();
    private SegmentBuilder activeSegment;
    private long nextSequence;
    private long eventCount;
    private boolean closed;
    private StorageIndexState indexState = StorageIndexState.EMPTY;

    private DamageEventJournal(Path directory) {
        this.directory = directory;
        activeSegment = new SegmentBuilder(0);
    }

    public static DamageEventJournal open(Path statsDirectory) {
        DamageEventJournal journal = new DamageEventJournal(statsDirectory.resolve(DIRECTORY_NAME));
        journal.initialize();
        return journal;
    }

    public void append(DamageRecord record) {
        if(closed) return;
        pending.addLast(new JournalEntry(nextSequence++, record));
        eventCount++;
    }

    /** 每次批量落盘最多重写一个段索引，原始事件帧则直接追加到活跃段。 */
    public void flush() {
        if(closed || pending.isEmpty()) return;
        try {
            Files.createDirectories(directory);
            while(!pending.isEmpty()) {
                if(activeSegment.isFull() && !sealActiveSegment()) return;
                writePendingToActiveSegment();
            }
        } catch (IOException e) {
            LOGGER.error("写出原始伤害事件段失败：{}", segmentPath(activeSegment.id), e);
        }
    }

    /** 正常关闭时封存未满段并写入索引；异常关闭的活跃段会在下次启动时自动恢复。 */
    public void close() {
        if(closed) return;
        flush();
        if(!activeSegment.isEmpty()) sealActiveSegment();
        closed = true;
    }

    /** 服务端查询只读取可能命中的段，最终仍逐条匹配，索引误命中不会影响准确性。 */
    public void forEachMatching(StatsFilter filter, Consumer<DamageRecord> consumer) {
        sealedSegments.stream()
                .filter(metadata -> metadata.mayMatch(filter))
                .sorted(Comparator.comparingLong(SegmentMetadata::firstSequence))
                .forEach(metadata -> readRecords(segmentPath(metadata.id), filter, consumer));
        if(!activeSegment.isEmpty() && activeSegment.build().mayMatch(filter)) {
            readRecords(segmentPath(activeSegment.id), filter, consumer);
        }
        pending.stream()
                .filter(entry -> visibleToFilter(entry, filter) && filter.matches(entry.record()))
                .map(JournalEntry::record)
                .forEach(consumer);
    }

    public List<DamageRecord> matching(StatsFilter filter) {
        List<DamageRecord> matched = new ArrayList<>();
        forEachMatching(filter, matched::add);
        return matched;
    }

    public Set<ResourceLocation> recordedSourceTypes() {
        return recordedTypes(SegmentMetadata::sourceTypes, record -> record.source().typeIdOrEnvironment());
    }

    public Set<ResourceLocation> recordedTargetTypes() {
        return recordedTypes(SegmentMetadata::targetTypes, record -> record.target().typeIdOrEnvironment());
    }

    public Set<ResourceLocation> recordedDirectSourceTypes() {
        return recordedTypes(SegmentMetadata::directSourceTypes, record -> record.directSource().typeIdOrEnvironment());
    }

    public Set<ResourceLocation> recordedDamageTypes() {
        return recordedTypes(SegmentMetadata::damageTypes, record -> record.damageTypeId());
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
        Long previous = resetSequences.put(owner.id(), nextSequence);
        if(writeResetSequences()) return true;
        if(previous == null) resetSequences.remove(owner.id());
        else resetSequences.put(owner.id(), previous);
        return false;
    }

    /** 组合筛选没有可复用的预聚合会话时，按完整事件历史切分当前一场战斗。 */
    public long currentSessionStart(StatsFilter filter, long gameTime, int timeoutTicks) {
        long[] start = {-1};
        long[] last = {-1};
        forEachMatching(filter, record -> {
            if(last[0] < 0 || record.gameTime() - last[0] > timeoutTicks) start[0] = record.gameTime();
            last[0] = record.gameTime();
        });
        return last[0] < 0 || gameTime - last[0] > timeoutTicks ? gameTime + 1 : start[0];
    }

    public boolean clear() {
        if(!clearDirectoryAtomically()) return false;
        pending.clear();
        sealedSegments.clear();
        resetSequences.clear();
        activeSegment = new SegmentBuilder(0);
        nextSequence = 0;
        eventCount = 0;
        closed = false;
        indexState = StorageIndexState.EMPTY;
        return true;
    }

    /** 准备空目录后切换；切换失败时恢复旧目录，避免内存和磁盘状态分离。 */
    private boolean clearDirectoryAtomically() {
        Path parent = directory.getParent();
        Path quarantined = directory.resolveSibling(directory.getFileName() + ".clearing-" + UUID.randomUUID());
        Path replacement = directory.resolveSibling(directory.getFileName() + ".clearing-new-" + UUID.randomUUID());
        boolean oldMoved = false;
        boolean replacementMoved = false;
        try {
            if(parent != null) Files.createDirectories(parent);
            Files.createDirectory(replacement);
            if(Files.exists(directory)) {
                moveDirectory(directory, quarantined);
                oldMoved = true;
            }
            moveDirectory(replacement, directory);
            replacementMoved = true;
        } catch (IOException e) {
            if(oldMoved && !Files.exists(directory)) {
                try {
                    moveDirectory(quarantined, directory);
                } catch (IOException restoreFailure) {
                    LOGGER.error("切换原始伤害事件目录失败且无法恢复旧目录：{}", directory, restoreFailure);
                }
            }
            if(!replacementMoved) deleteTree(replacement);
            LOGGER.error("切换原始伤害事件目录失败：{}", directory, e);
            return false;
        }
        deleteTree(quarantined);
        return true;
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private void deleteTree(Path root) {
        if(!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteFile);
        } catch (IOException e) {
            LOGGER.warn("清理已隔离的原始伤害事件目录失败：{}，该目录不会再次参与统计读取", root, e);
        }
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
        flush();
        if(!pending.isEmpty()) return false;
        if(!activeSegment.isEmpty()) sealActiveSegment();
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
        try (Stream<String> lines = Files.lines(legacyPath)) {
            for (String line : lines.toList()) {
                LegacyJournalEntry.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(line))
                        .resultOrPartial(error -> LOGGER.error("旧版原始伤害事件解析失败：{}", error))
                        .ifPresent(entries::add);
            }
            return true;
        } catch (IOException | RuntimeException e) {
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
        indexState = writeIndex() ? StorageIndexState.LOADED : StorageIndexState.FAILED;
        activeSegment = new SegmentBuilder(activeSegment.id + 1);
        return true;
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
            return new JournalEntry(sequence, new DamageRecord(
                    source, directSource, target, damageType, original, actual, blocked, reduction,
                    gameTime, lethal, dimension, blockX, blockY, blockZ, occurredAt));
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
