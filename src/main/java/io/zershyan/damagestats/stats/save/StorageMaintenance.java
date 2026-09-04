package io.zershyan.damagestats.stats.save;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;
import java.util.stream.Stream;

/** 只管理本模组服务器目录中的辅助文件，权威事件由 DamageEventJournal 单独持有。 */
public final class StorageMaintenance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EVENTS_DIRECTORY = "events";
    private static final String CLEARING_DIRECTORY_PREFIX = "events.clearing-";
    private static final String EXPORTS_DIRECTORY = "exports";
    private static final String CACHE_FILE = "stats.json";
    private static final String INDEX_FILE = "index.bin";

    private StorageMaintenance() {}

    public static StorageOverview overview(Path storageDirectory, @Nullable DamageEventJournal journal) {
        Path eventsDirectory = storageDirectory.resolve(EVENTS_DIRECTORY);
        FileTotals rawSegments = totals(eventsDirectory, name -> name.matches("segment-\\d+\\.bin"));
        FileTotals compressedSegments = totals(eventsDirectory, name -> name.matches("segment-\\d+\\.bin\\.gz"));
        FileTotals index = totals(eventsDirectory, INDEX_FILE::equals);
        FileTotals cache = totals(storageDirectory, CACHE_FILE::equals);
        FileTotals exports = totals(storageDirectory.resolve(EXPORTS_DIRECTORY), StorageMaintenance::isExportFile);
        FileTotals temporary = totals(storageDirectory, name -> name.endsWith(".tmp"));
        FileTotals backups = totals(eventsDirectory, name -> name.endsWith(".jsonl.migrated"));
        long eventCount = journal == null ? 0 : journal.eventCount();
        int segmentCount = journal == null ? rawSegments.count() + compressedSegments.count() : journal.segmentCount();
        StorageIndexState indexState = journal == null ? StorageIndexState.EMPTY : journal.indexState();
        return new StorageOverview(eventCount, segmentCount, rawSegments.bytes(), compressedSegments.bytes(),
                index.bytes(), cache.bytes(), exports.bytes(), temporary.bytes(), backups.bytes(),
                totals(storageDirectory, ignored -> true).bytes(), indexState);
    }

    public static void cleanTemporaryAndBackups(Path storageDirectory) {
        deleteMatching(storageDirectory, name -> name.endsWith(".tmp"));
        deleteMatching(storageDirectory.resolve(EVENTS_DIRECTORY), name -> name.endsWith(".jsonl.migrated"));
        deleteClearingDirectories(storageDirectory);
    }

    public static void cleanExports(Path storageDirectory) {
        deleteMatching(storageDirectory.resolve(EXPORTS_DIRECTORY), StorageMaintenance::isExportFile);
    }

    private static FileTotals totals(Path directory, Predicate<String> fileNameMatches) {
        if(!Files.isDirectory(directory)) return FileTotals.EMPTY;
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> fileNameMatches.test(path.getFileName().toString()))
                    .mapToLong(StorageMaintenance::size)
                    .collect(FileTotals::new, FileTotals::add, FileTotals::merge);
        } catch (IOException e) {
            LOGGER.warn("读取伤害统计存储概览失败：{}", directory, e);
            return FileTotals.EMPTY;
        }
    }

    private static void deleteMatching(Path directory, Predicate<String> fileNameMatches) {
        if(!Files.isDirectory(directory)) return;
        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> fileNameMatches.test(path.getFileName().toString()))
                    .forEach(StorageMaintenance::delete);
        } catch (IOException e) {
            LOGGER.warn("清理伤害统计存储文件失败：{}", directory, e);
        }
    }

    private static void deleteClearingDirectories(Path storageDirectory) {
        if(!Files.isDirectory(storageDirectory)) return;
        try (Stream<Path> paths = Files.list(storageDirectory)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(CLEARING_DIRECTORY_PREFIX))
                    .forEach(StorageMaintenance::deleteTree);
        } catch (IOException e) {
            LOGGER.warn("清理隔离的原始伤害事件目录失败：{}", storageDirectory, e);
        }
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(StorageMaintenance::delete);
        } catch (IOException e) {
            LOGGER.warn("删除隔离目录失败：{}", root, e);
        }
    }

    private static boolean isExportFile(String fileName) {
        return fileName.startsWith("damagestats-")
                && (fileName.endsWith(".json") || fileName.endsWith(".csv"));
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            LOGGER.warn("读取伤害统计文件大小失败：{}", path, e);
            return 0;
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("清理伤害统计文件失败：{}", path, e);
        }
    }

    private static final class FileTotals {
        private static final FileTotals EMPTY = new FileTotals();
        private long bytes;
        private int count;

        private void add(long size) {
            bytes += size;
            count++;
        }

        private void merge(FileTotals other) {
            bytes += other.bytes;
            count += other.count;
        }

        private long bytes() {
            return bytes;
        }

        private int count() {
            return count;
        }
    }
}
