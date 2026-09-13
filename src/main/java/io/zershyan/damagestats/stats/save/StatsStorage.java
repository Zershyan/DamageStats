package io.zershyan.damagestats.stats.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 统计数据的读写。文件放在世界目录下，于是「每个存档独立」是目录结构天然保证的，
 * 不需要额外的隔离逻辑；单人换存档、服务器换世界都自动分开。
 */
public final class StatsStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "damagestats";
    private static final String FILE_NAME = "stats.json";
    private static final String FOCUS_WORLD_ID_FILE_NAME = "focus-world-id.txt";
    private static final String EVENTS_DIRECTORY_NAME = "events";
    private static final String RESET_TRANSACTION_PREFIX = ".reset-";
    private static final String RESET_STATE_FILE_NAME = "state";
    private static final String RESET_MANIFEST_FILE_NAME = "manifest";
    private static final String RESET_STAGED_STATS_NAME = "stats.json.new";
    private static final String RESET_STAGED_EVENTS_NAME = "events.new";
    private static final String RESET_STATE_PREPARED = "PREPARED";
    private static final String RESET_STATE_COMMITTED = "COMMITTED";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
    }

    /** 读不出来就返回 null，让调用方从权威事件日志恢复；存档损坏不该阻止世界进入。 */
    public static @Nullable DamageTracker load(MinecraftServer server) {
        return load(directory(server));
    }

    public static @Nullable DamageTracker load(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        if(!Files.exists(file)) return null;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            return DamageTracker.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(error -> LOGGER.error("伤害统计存档解析失败：{}", error))
                    .orElse(null);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("读取伤害统计存档失败，将由启动流程从事件日志恢复", e);
            return null;
        }
    }

    /** 传目录而不是 MinecraftServer：崩服兜底时存档会话已经关了，那时再问 getWorldPath 未必安全 */
    public static boolean save(Path directory, DamageTracker tracker) {
        Path file = directory.resolve(FILE_NAME);
        return DamageTracker.CODEC.encodeStart(JsonOps.INSTANCE, tracker)
                .resultOrPartial(error -> LOGGER.error("伤害统计序列化失败：{}", error))
                .map(json -> write(file, json))
                .orElse(false);
    }

    public static boolean invalidateCache(Path directory) {
        Path file = directory.resolve(FILE_NAME);
        try {
            Files.deleteIfExists(file);
            return true;
        } catch (IOException e) {
            LOGGER.error("清除伤害统计聚合缓存失败：{}", file, e);
            return false;
        }
    }

    /**
     * 以可恢复事务清空完整统计。事件日志和聚合缓存是两个独立文件系统对象，
     * 因而除了提交标记，还必须在启动时处理进程中断留下的事务目录。
     */
    public static boolean resetAllAtomically(Path directory, DamageEventJournal journal,
                                              DamageTracker currentTracker) {
        if(journal.isClearing()) {
            ResetTransactionScan scan = resetTransactions(directory, null);
            if(!scan.success()) return false;
            boolean hasCommitted = false;
            for(Path transaction : scan.transactions()) {
                String state = readResetText(transaction.resolve(RESET_STATE_FILE_NAME));
                if(RESET_STATE_PREPARED.equals(state)) {
                    if(!rollbackResetTransaction(directory, transaction,
                            new IOException("重试全量清理前回滚未提交事务"))) return false;
                    continue;
                }
                if(RESET_STATE_COMMITTED.equals(state)) {
                    hasCommitted = true;
                    continue;
                }
                LOGGER.error("伤害统计清理事务状态未知，无法重试：{}，状态：{}", transaction, state);
                return false;
            }
            if(hasCommitted) return retryCommittedReset(directory, journal, currentTracker);
            journal.abortClear();
        }
        if(!journal.beginClear()) return false;
        if(!recoverTemporaryResets(directory)) {
            LOGGER.error("发现无法恢复的伤害统计清理事务，已继续阻止事件与聚合写入：{}", directory);
            return false;
        }

        Path transaction = directory.resolve(RESET_TRANSACTION_PREFIX + UUID.randomUUID());
        Path statsFile = directory.resolve(FILE_NAME);
        Path eventsDirectory = directory.resolve(EVENTS_DIRECTORY_NAME);
        boolean committed = false;
        boolean transactionCreated = false;
        try {
            Files.createDirectories(directory);
            Files.createDirectory(transaction);
            transactionCreated = true;

            boolean statsPresent = Files.exists(statsFile);
            boolean eventsPresent = Files.exists(eventsDirectory);
            writeResetText(transaction.resolve(RESET_MANIFEST_FILE_NAME),
                    (statsPresent ? "stats=1" : "stats=0") + System.lineSeparator()
                            + (eventsPresent ? "events=1" : "events=0") + System.lineSeparator());
            writeResetText(transaction.resolve(RESET_STATE_FILE_NAME), RESET_STATE_PREPARED);

            if(!write(transaction.resolve(RESET_STAGED_STATS_NAME),
                    DamageTracker.CODEC.encodeStart(JsonOps.INSTANCE, new DamageTracker())
                            .resultOrPartial(error -> LOGGER.error("清理时序列化空伤害统计失败：{}", error))
                            .orElse(null))) {
                throw new IOException("无法准备空伤害统计缓存");
            }
            Files.createDirectory(transaction.resolve(RESET_STAGED_EVENTS_NAME));

            moveIfPresent(statsFile, transaction.resolve(FILE_NAME));
            moveIfPresent(eventsDirectory, transaction.resolve(EVENTS_DIRECTORY_NAME));
            movePath(transaction.resolve(RESET_STAGED_EVENTS_NAME), eventsDirectory);
            movePath(transaction.resolve(RESET_STAGED_STATS_NAME), statsFile);
            writeResetText(transaction.resolve(RESET_STATE_FILE_NAME), RESET_STATE_COMMITTED);
            committed = true;

            if(!clearMemoryAfterCommit(transaction, journal, currentTracker)) return false;
            if(!tryDeleteTree(transaction)) {
                LOGGER.warn("伤害统计清理已提交，事务备份目录将在后续维护时删除：{}", transaction);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            if(committed) {
                LOGGER.error("伤害统计清理事务已提交，但事务后处理失败：{}", transaction, e);
                return true;
            }
            boolean rolledBack = !transactionCreated || rollbackResetTransaction(directory, transaction, e);
            if(rolledBack) journal.abortClear();
            else LOGGER.error("伤害统计清理回滚未完成，继续阻止新事件写入，等待下次恢复：{}", transaction);
            return false;
        }
    }

    /** 上一次磁盘提交后若内存清理失败，保留阻塞状态并允许管理员再次触发重试。 */
    private static boolean retryCommittedReset(Path directory, DamageEventJournal journal,
                                                DamageTracker currentTracker) {
        ResetTransactionScan scan = resetTransactions(directory, RESET_STATE_COMMITTED);
        if(!scan.success()) return false;
        List<Path> committedTransactions = scan.transactions();
        if(committedTransactions.isEmpty()) return false;
        for(Path transaction : committedTransactions) {
            if(readResetManifest(transaction.resolve(RESET_MANIFEST_FILE_NAME)) == null
                    || !committedTargetsReady(directory, transaction)) return false;
        }
        Path transaction = committedTransactions.get(committedTransactions.size() - 1);
        if(!clearMemoryAfterCommit(transaction, journal, currentTracker)) return false;
        boolean success = true;
        for(Path committed : committedTransactions) success = tryDeleteTree(committed) && success;
        return success;
    }

    private static boolean clearMemoryAfterCommit(Path transaction, DamageEventJournal journal,
                                                  DamageTracker currentTracker) {
        try {
            currentTracker.reset();
        } catch (RuntimeException e) {
            // 磁盘已经提交，不能放行仍可能含旧聚合的内存状态。
            LOGGER.error("伤害统计清理已提交，但聚合缓存清理失败，保持阻塞等待重试：{}", transaction, e);
            return false;
        }
        try {
            journal.completeClear();
            return true;
        } catch (RuntimeException e) {
            // 事件查询缓存未清空时同样不能恢复追加，否则旧事件可能回写到新目录。
            LOGGER.error("伤害统计清理已提交，但事件查询状态清理失败，保持阻塞等待重试：{}", transaction, e);
            return false;
        }
    }

    /** 服务端启动早期恢复上一次未完成的全量清理事务。 */
    public static boolean recoverPendingReset(Path directory) {
        return recoverTemporaryResets(directory);
    }

    /** 自动保存关闭时，显式重置仍要覆盖旧文件，防止之后重新开启自动保存时数据复活。 */
    public static boolean resetPersisted(Path directory, @Nullable EntityRef owner) {
        DamageTracker tracker = load(directory);
        if(tracker == null) tracker = new DamageTracker();
        if(owner == null) tracker.reset();
        else tracker.resetFor(owner);
        return save(directory, tracker);
    }

    /** 仅向客户端公开随机世界标识，避免将服务端文件路径作为客户端偏好键的一部分传输。 */
    public static String focusWorldId(Path directory) {
        Path file = directory.resolve(FOCUS_WORLD_ID_FILE_NAME);
        try {
            Files.createDirectories(directory);
            if(Files.isRegularFile(file)) return UUID.fromString(Files.readString(file, StandardCharsets.UTF_8).trim()).toString();
            String worldId = UUID.randomUUID().toString();
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, worldId, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return worldId;
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("读取或写入焦点世界标识失败，本次连接不持久化类型焦点", e);
            return "";
        }
    }

    private static boolean write(Path file, JsonElement json) {
        if(json == null) return false;
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(json, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException | FileAlreadyExistsException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("写出伤害统计存档失败：{}", file, e);
            return false;
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException e) {
                LOGGER.warn("清理伤害统计临时文件失败：{}", temporary, e);
            }
        }
    }

    private static boolean rollbackResetTransaction(Path directory, Path transaction, Exception cause) {
        ResetManifest manifest = readResetManifest(transaction.resolve(RESET_MANIFEST_FILE_NAME));
        if(manifest == null) {
            LOGGER.error("伤害统计清理事务缺少恢复清单，保留现场等待后续处理：{}", transaction, cause);
            return false;
        }
        try {
            Path statsFile = directory.resolve(FILE_NAME);
            Path eventsDirectory = directory.resolve(EVENTS_DIRECTORY_NAME);
            Path statsBackup = transaction.resolve(FILE_NAME);
            Path eventsBackup = transaction.resolve(EVENTS_DIRECTORY_NAME);

            if(manifest.statsPresent()) {
                if(Files.exists(statsBackup)) {
                    deleteAny(statsFile);
                    movePath(statsBackup, statsFile);
                } else if(!Files.exists(statsFile)) {
                    throw new IOException("统计缓存原文件和事务备份均不存在");
                }
            } else {
                deleteAny(statsFile);
            }
            if(manifest.eventsPresent()) {
                if(Files.exists(eventsBackup)) {
                    deleteAny(eventsDirectory);
                    movePath(eventsBackup, eventsDirectory);
                } else if(!Files.exists(eventsDirectory)) {
                    throw new IOException("事件目录原文件和事务备份均不存在");
                }
            } else {
                deleteAny(eventsDirectory);
            }
            if(!tryDeleteTree(transaction)) return false;
            LOGGER.warn("已回滚未提交的伤害统计清理事务：{}", transaction, cause);
            return true;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("回滚伤害统计清理事务失败，保留事务目录等待下次启动恢复：{}", transaction, e);
            return false;
        }
    }

    private static ResetManifest readResetManifest(Path file) {
        if(!Files.isRegularFile(file)) return null;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if(lines.size() != 2) return null;
            Boolean statsPresent = null;
            Boolean eventsPresent = null;
            for(String line : lines) {
                switch (line) {
                    case "stats=0" -> {
                        if(statsPresent != null) return null;
                        statsPresent = false;
                    }
                    case "stats=1" -> {
                        if(statsPresent != null) return null;
                        statsPresent = true;
                    }
                    case "events=0" -> {
                        if(eventsPresent != null) return null;
                        eventsPresent = false;
                    }
                    case "events=1" -> {
                        if(eventsPresent != null) return null;
                        eventsPresent = true;
                    }
                    default -> {
                        return null;
                    }
                }
            }
            return statsPresent == null || eventsPresent == null
                    ? null : new ResetManifest(statsPresent, eventsPresent);
        } catch (IOException e) {
            LOGGER.error("读取伤害统计清理事务清单失败：{}", file, e);
            return null;
        }
    }

    private static String readResetText(Path file) {
        try {
            return Files.isRegularFile(file)
                    ? Files.readString(file, StandardCharsets.UTF_8).trim() : "";
        } catch (IOException e) {
            LOGGER.error("读取伤害统计清理事务状态失败：{}", file, e);
            return "";
        }
    }

    private static void writeResetText(Path file, String text) throws IOException {
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void moveIfPresent(Path source, Path target) throws IOException {
        if(Files.exists(source)) movePath(source, target);
    }

    private static void movePath(Path source, Path target) throws IOException {
        if(!Files.exists(source)) throw new NoSuchFileException(source.toString());
        if(Files.exists(target)) throw new FileAlreadyExistsException(target.toString());
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
    }

    private static void deleteAny(Path path) {
        if(!Files.exists(path)) return;
        if(Files.isDirectory(path)) {
            deleteTree(path);
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("删除伤害统计文件失败：" + path, e);
        }
    }

    static boolean recoverTemporaryResets(Path directory) {
        ResetTransactionScan scan = resetTransactions(directory, null);
        if(!scan.success()) return false;
        boolean success = true;
        for(Path transaction : scan.transactions()) {
            String state = readResetText(transaction.resolve(RESET_STATE_FILE_NAME));
            if(RESET_STATE_PREPARED.equals(state)) {
                success = rollbackResetTransaction(directory, transaction,
                        new IOException("存储维护前恢复未提交事务")) && success;
                continue;
            }
            if(RESET_STATE_COMMITTED.equals(state)) {
                if(readResetManifest(transaction.resolve(RESET_MANIFEST_FILE_NAME)) == null) {
                    LOGGER.error("已提交的伤害统计清理事务清单损坏，将保留现场：{}", transaction);
                    success = false;
                } else if(!committedTargetsReady(directory, transaction)) {
                    success = false;
                } else success = tryDeleteTree(transaction) && success;
                continue;
            }
            LOGGER.error("伤害统计清理事务状态未知，将保留现场：{}，状态：{}", transaction, state);
            success = false;
        }
        return success;
    }

    private static boolean committedTargetsReady(Path directory, Path transaction) {
        if(Files.isRegularFile(directory.resolve(FILE_NAME))
                && Files.isDirectory(directory.resolve(EVENTS_DIRECTORY_NAME))) return true;
        LOGGER.error("已提交的伤害统计清理事务缺少新的空数据文件，将保留事务现场：{}", transaction);
        return false;
    }

    private static ResetTransactionScan resetTransactions(Path directory, @Nullable String requiredState) {
        if(!Files.isDirectory(directory)) return new ResetTransactionScan(true, List.of());
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> transactions = paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith(RESET_TRANSACTION_PREFIX))
                    .filter(path -> requiredState == null
                            || requiredState.equals(readResetText(path.resolve(RESET_STATE_FILE_NAME))))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            return new ResetTransactionScan(true, transactions);
        } catch (IOException e) {
            LOGGER.error("查找伤害统计清理事务失败：{}", directory, e);
            return new ResetTransactionScan(false, List.of());
        }
    }

    private static void deleteTree(Path root) {
        if(!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(StatsStorage::deletePathUnchecked);
        } catch (IOException e) {
            throw new UncheckedIOException("删除伤害统计事务目录失败：" + root, e);
        }
    }

    private static boolean tryDeleteTree(Path root) {
        try {
            deleteTree(root);
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("删除伤害统计事务目录失败：{}", root, e);
            return false;
        }
    }

    private static void deletePath(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    private static void deletePathUnchecked(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("删除伤害统计文件失败：" + path, e);
        }
    }

    private record ResetManifest(boolean statsPresent, boolean eventsPresent) {}

    private record ResetTransactionScan(boolean success, List<Path> transactions) {}
}
