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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;

/**
 * 统计数据的读写。文件放在世界目录下，于是「每个存档独立」是目录结构天然保证的，
 * 不需要额外的隔离逻辑；单人换存档、服务器换世界都自动分开。
 */
public final class StatsStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "damagestats";
    private static final String FILE_NAME = "stats.json";
    private static final String FOCUS_WORLD_ID_FILE_NAME = "focus-world-id.txt";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
    }

    /** 读不出来就返回 null 让调用方从空数据开始——存档里的统计坏了不该连世界都进不去 */
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
            LOGGER.error("读取伤害统计存档失败，本次从空数据开始", e);
            return null;
        }
    }

    /** 传目录而不是 MinecraftServer：崩服兜底时存档会话已经关了，那时再问 getWorldPath 未必安全 */
    public static void save(Path directory, DamageTracker tracker) {
        Path file = directory.resolve(FILE_NAME);
        DamageTracker.CODEC.encodeStart(JsonOps.INSTANCE, tracker)
                .resultOrPartial(error -> LOGGER.error("伤害统计序列化失败：{}", error))
                .ifPresent(json -> write(file, json));
    }

    /** 自动保存关闭时，显式重置仍要覆盖旧文件，防止之后重新开启自动保存时数据复活。 */
    public static void resetPersisted(Path directory, @Nullable EntityRef owner) {
        DamageTracker tracker = load(directory);
        if(tracker == null) tracker = new DamageTracker();
        if(owner == null) tracker.reset();
        else tracker.resetFor(owner);
        save(directory, tracker);
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

    private static void write(Path file, JsonElement json) {
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
        } catch (IOException e) {
            LOGGER.error("写出伤害统计存档失败：{}", file, e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException e) {
                LOGGER.warn("清理伤害统计临时文件失败：{}", temporary, e);
            }
        }
    }
}
