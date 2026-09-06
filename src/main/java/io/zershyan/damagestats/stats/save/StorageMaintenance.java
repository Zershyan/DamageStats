package io.zershyan.damagestats.stats.save;

import com.mojang.logging.LogUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** 只管理本模组服务器目录中的辅助文件，权威事件由 DamageEventJournal 单独持有。 */
public final class StorageMaintenance {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String EVENTS_DIRECTORY = "events";
    private static final String CLEARING_DIRECTORY_PREFIX = "events.clearing-";
    private static final String EXPORTS_DIRECTORY = "exports";
    private static final String CACHE_FILE = "stats.json";
    private static final String INDEX_FILE = "index.bin";
    private static final String RESET_FILE = "resets.bin";
    private static final Pattern RAW_SEGMENT = Pattern.compile("segment-\\d+\\.bin");
    private static final Pattern COMPRESSED_SEGMENT = Pattern.compile("segment-\\d+\\.bin\\.gz");
    private static final Pattern MIGRATED_BACKUP = Pattern.compile("segment-\\d+-\\d+\\.jsonl\\.migrated");
    private static final Pattern EXPORT_FILE = Pattern.compile("damagestats-[^/]+\\.(?:json|csv)");

    private StorageMaintenance() {}

    public static StorageOverview overview(Path storageDirectory, @Nullable DamageEventJournal journal) {
        Path eventsDirectory = storageDirectory.resolve(EVENTS_DIRECTORY);
        FileTotals rawSegments = totals(eventsDirectory, name -> RAW_SEGMENT.matcher(name).matches());
        FileTotals compressedSegments = totals(eventsDirectory, name -> COMPRESSED_SEGMENT.matcher(name).matches());
        FileTotals index = totals(eventsDirectory, name -> INDEX_FILE.equals(name) || RESET_FILE.equals(name));
        FileTotals cache = totals(storageDirectory, CACHE_FILE::equals);
        FileTotals exports = totals(storageDirectory.resolve(EXPORTS_DIRECTORY), StorageMaintenance::isExportFile);
        FileTotals temporary = temporaryTotals(storageDirectory);
        FileTotals backups = totals(eventsDirectory, name -> MIGRATED_BACKUP.matcher(name).matches());
        long eventCount = journal == null ? 0 : journal.eventCount();
        int segmentCount = journal == null ? rawSegments.count() + compressedSegments.count() : journal.segmentCount();
        StorageIndexState indexState = journal == null ? StorageIndexState.EMPTY : journal.indexState();
        return new StorageOverview(eventCount, segmentCount, rawSegments.bytes(), compressedSegments.bytes(),
                index.bytes(), cache.bytes(), exports.bytes(), temporary.bytes(), backups.bytes(),
                recursiveTotals(storageDirectory, ignored -> true).bytes(), indexState);
    }

    public static boolean cleanTemporaryAndBackups(Path storageDirectory) {
        boolean success = deleteMatching(storageDirectory, name -> name.endsWith(".tmp"));
        success = deleteMatching(storageDirectory.resolve(EVENTS_DIRECTORY),
                name -> MIGRATED_BACKUP.matcher(name).matches()) && success;
        success = deleteClearingDirectories(storageDirectory) && success;
        return success;
    }

    public static boolean cleanExports(Path storageDirectory) {
        return deleteMatching(storageDirectory.resolve(EXPORTS_DIRECTORY), StorageMaintenance::isExportFile);
    }

    private static FileTotals totals(Path directory, Predicate<String> fileNameMatches) {
        if(!Files.isDirectory(directory)) return FileTotals.EMPTY;
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> fileNameMatches.test(path.getFileName().toString()))
                    .mapToLong(StorageMaintenance::size)
                    .collect(FileTotals::new, FileTotals::add, FileTotals::merge);
        } catch (IOException e) {
            LOGGER.warn("读取伤害统计存储概览失败：{}", directory, e);
            return FileTotals.EMPTY;
        }
    }

    private static FileTotals recursiveTotals(Path directory, Predicate<Path> pathMatches) {
        if(!Files.isDirectory(directory)) return FileTotals.EMPTY;
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(pathMatches)
                    .mapToLong(StorageMaintenance::size)
                    .collect(FileTotals::new, FileTotals::add, FileTotals::merge);
        } catch (IOException e) {
            LOGGER.warn("读取伤害统计存储概览失败：{}", directory, e);
            return FileTotals.EMPTY;
        }
    }

    private static FileTotals temporaryTotals(Path storageDirectory) {
        return recursiveTotals(storageDirectory, path -> path.getFileName().toString().endsWith(".tmp")
                || isInClearingDirectory(storageDirectory, path));
    }

    private static boolean isInClearingDirectory(Path storageDirectory, Path path) {
        Path relative = storageDirectory.relativize(path);
        return relative.getNameCount() > 0
                && relative.getName(0).toString().startsWith(CLEARING_DIRECTORY_PREFIX);
    }

    private static boolean deleteMatching(Path directory, Predicate<String> fileNameMatches) {
        if(!Files.isDirectory(directory)) return true;
        List<Path> matches;
        try (Stream<Path> files = Files.walk(directory)) {
            matches = files.filter(Files::isRegularFile)
                    .filter(path -> fileNameMatches.test(path.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            LOGGER.warn("清理伤害统计存储文件失败：{}", directory, e);
            return false;
        }
        boolean success = true;
        for(Path path : matches) success = delete(path) && success;
        return success;
    }

    private static boolean deleteClearingDirectories(Path storageDirectory) {
        if(!Files.isDirectory(storageDirectory)) return true;
        List<Path> directories;
        try (Stream<Path> paths = Files.list(storageDirectory)) {
            directories = paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(CLEARING_DIRECTORY_PREFIX))
                    .toList();
        } catch (IOException e) {
            LOGGER.warn("清理隔离的原始伤害事件目录失败：{}", storageDirectory, e);
            return false;
        }
        boolean success = true;
        for(Path directory : directories) success = deleteTree(directory) && success;
        return success;
    }

    private static boolean deleteTree(Path root) {
        List<Path> paths;
        try (Stream<Path> walked = Files.walk(root)) {
            paths = walked.sorted(Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            LOGGER.warn("删除隔离目录失败：{}", root, e);
            return false;
        }
        boolean success = true;
        for(Path path : paths) success = delete(path) && success;
        return success;
    }

    private static boolean isExportFile(String fileName) {
        return EXPORT_FILE.matcher(fileName).matches();
    }

    private static long size(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            LOGGER.warn("读取伤害统计文件大小失败：{}", path, e);
            return 0;
        }
    }

    private static boolean delete(Path path) {
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException e) {
            LOGGER.warn("清理伤害统计文件失败：{}", path, e);
            return false;
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
