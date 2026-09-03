package io.zershyan.damagestats.stats.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import io.zershyan.damagestats.stats.view.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 导出统计。刻意只依赖视图 DTO，于是服务端指令和客户端界面按钮共用同一份格式化代码。
 * JSON 是完整的，CSV 只放分组明细——那部分才是真正表格状的数据，适合丢进 Excel 画图。
 */
public final class StatsExporter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXPORT_DIR = "exports";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String CSV_HEADER = "direction,scope,dimension,name,damage,share_percent,hits";

    /** 返回导出目录，失败返回 null。文件名带时间戳，不会覆盖旧的导出 */
    public static @Nullable Path export(StatsSnapshot snapshot, Path baseDirectory) {
        Path directory = baseDirectory.resolve(EXPORT_DIR);
        String stamp = LocalDateTime.now().format(FILE_STAMP);
        try {
            Files.createDirectories(directory);
            writeJson(directory.resolve("damagestats-" + stamp + ".json"), snapshot);
            writeCsv(directory.resolve("damagestats-" + stamp + ".csv"), snapshot);
            return directory;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("导出伤害统计失败：{}", directory, e);
            return null;
        }
    }

    private static void writeJson(Path file, StatsSnapshot snapshot) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("exportedAt", LocalDateTime.now().toString());
        root.add("dealt", entryJson(snapshot.outgoing()));
        root.add("taken", entryJson(snapshot.incoming()));
        JsonArray fights = new JsonArray();
        snapshot.history().forEach(session -> {
            JsonObject object = new JsonObject();
            object.addProperty("damage", session.totalDamage());
            object.addProperty("dps", session.averageDps());
            object.addProperty("durationSeconds", session.durationSeconds());
            object.addProperty("hits", session.hitCount());
            fights.add(object);
        });
        root.add("finishedFights", fights);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static JsonObject entryJson(EntryView entry) {
        JsonObject object = new JsonObject();
        object.addProperty("owner", entry.ownerName().getString());
        object.add("currentFight", viewJson(entry.session()));
        object.add("lifetime", viewJson(entry.lifetime()));
        return object;
    }

    private static JsonObject viewJson(StatsView view) {
        JsonObject object = new JsonObject();
        object.add("metrics", metricsJson(view.metrics()));
        object.add("byDamageType", groupsJson(view.byType()));
        object.add("byDirectSource", groupsJson(view.bySource()));
        object.add("byOpponent", groupsJson(view.byOpponent()));
        return object;
    }

    private static JsonObject metricsJson(MetricsView metrics) {
        JsonObject object = new JsonObject();
        object.addProperty("totalDamage", metrics.totalActual());
        object.addProperty("originalDamage", metrics.totalOriginal());
        object.addProperty("reducedDamage", metrics.reducedDamage());
        object.addProperty("reductionRate", metrics.reductionRate());
        object.addProperty("hits", metrics.hitCount());
        object.addProperty("kills", metrics.killCount());
        object.addProperty("averageHit", metrics.averageDamage());
        object.addProperty("maxHit", metrics.maxSingle());
        object.addProperty("maxHitType", metrics.maxSingleTypeName().getString());
        object.addProperty("minHit", metrics.minSingle());
        object.addProperty("averageDps", metrics.averageDps());
        object.addProperty("realtimeDps", metrics.realtimeDps());
        object.addProperty("hitsPerSecond", metrics.hitsPerSecond());
        object.addProperty("durationSeconds", metrics.durationSeconds());
        return object;
    }

    private static JsonArray groupsJson(List<GroupView> groups) {
        JsonArray array = new JsonArray();
        groups.forEach(group -> {
            JsonObject object = new JsonObject();
            object.addProperty("name", group.name().getString());
            object.addProperty("damage", group.damage());
            object.addProperty("sharePercent", group.share() * 100);
            object.addProperty("hits", group.hitCount());
            array.add(object);
        });
        return array;
    }

    private static void writeCsv(Path file, StatsSnapshot snapshot) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(CSV_HEADER);
        appendEntry(lines, "dealt", snapshot.outgoing());
        appendEntry(lines, "taken", snapshot.incoming());
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    private static void appendEntry(List<String> lines, String direction, EntryView entry) {
        appendScope(lines, direction, "currentFight", entry.session());
        appendScope(lines, direction, "lifetime", entry.lifetime());
    }

    private static void appendScope(List<String> lines, String direction, String scope, StatsView view) {
        appendRows(lines, direction, scope, "damageType", view.byType());
        appendRows(lines, direction, scope, "directSource", view.bySource());
        appendRows(lines, direction, scope, "opponent", view.byOpponent());
    }

    private static void appendRows(List<String> lines, String direction, String scope,
                                   String dimension, List<GroupView> groups) {
        groups.forEach(group -> lines.add(String.join(",",
                direction,
                scope,
                dimension,
                csvField(group.name().getString()),
                number(group.damage()),
                number(group.share() * 100),
                String.valueOf(group.hitCount()))));
    }

    /** 实体可能被命名牌起了带逗号或引号的名字，按 CSV 的规矩转义掉 */
    private static String csvField(String value) {
        if(value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** 固定 Locale.US，否则小数点会跟随系统语言变成逗号，把 CSV 撑破 */
    private static String number(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private StatsExporter() {
    }
}
