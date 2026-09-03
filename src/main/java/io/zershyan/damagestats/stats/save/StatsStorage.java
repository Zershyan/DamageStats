package io.zershyan.damagestats.stats.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.JsonOps;
import io.zershyan.damagestats.stats.DamageTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 统计数据的读写。文件放在世界目录下，于是「每个存档独立」是目录结构天然保证的，
 * 不需要额外的隔离逻辑；单人换存档、服务器换世界都自动分开。
 */
public final class StatsStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR_NAME = "damagestats";
    private static final String FILE_NAME = "stats.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(DIR_NAME);
    }

    /** 读不出来就返回 null 让调用方从空数据开始——存档里的统计坏了不该连世界都进不去 */
    public static @Nullable DamageTracker load(MinecraftServer server) {
        Path file = directory(server).resolve(FILE_NAME);
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

    public static void save(MinecraftServer server, DamageTracker tracker) {
        Path file = directory(server).resolve(FILE_NAME);
        DamageTracker.CODEC.encodeStart(JsonOps.INSTANCE, tracker)
                .resultOrPartial(error -> LOGGER.error("伤害统计序列化失败：{}", error))
                .ifPresent(json -> write(file, json));
    }

    private static void write(Path file, JsonElement json) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            LOGGER.error("写出伤害统计存档失败：{}", file, e);
        }
    }
}
