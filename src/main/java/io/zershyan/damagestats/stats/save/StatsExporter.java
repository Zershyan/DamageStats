package io.zershyan.damagestats.stats.save;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import io.zershyan.damagestats.stats.DamageReduction;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.filter.DamageTypeSelector;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.FilterKey;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.view.GroupView;
import io.zershyan.damagestats.stats.view.MetricsView;
import io.zershyan.damagestats.stats.view.StatsSnapshot;
import io.zershyan.damagestats.stats.view.StatsView;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
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
    private static final Object EXPORT_LOCK = new Object();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String EXPORT_DIR = "exports";
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final String CSV_HEADER = "scope,dimension,name,damage,original_damage,reduced_damage,share_percent,hits";

    /** 返回导出目录，失败返回 null。文件名带时间戳，不会覆盖旧的导出 */
    public static @Nullable Path export(StatsSnapshot snapshot, Path baseDirectory) {
        synchronized (EXPORT_LOCK) {
            return exportLocked(snapshot, baseDirectory);
        }
    }

    private static @Nullable Path exportLocked(StatsSnapshot snapshot, Path baseDirectory) {
        Path directory = baseDirectory.resolve(EXPORT_DIR);
        String stamp = LocalDateTime.now().format(FILE_STAMP);
        Path jsonTemporary = null;
        Path csvTemporary = null;
        Path jsonFile = null;
        Path csvFile = null;
        try {
            Files.createDirectories(directory);
            String baseName = nextBaseName(directory, stamp);
            jsonFile = directory.resolve(baseName + ".json");
            csvFile = directory.resolve(baseName + ".csv");
            jsonTemporary = Files.createTempFile(directory, baseName + "-", ".json.tmp");
            csvTemporary = Files.createTempFile(directory, baseName + "-", ".csv.tmp");
            writeJson(jsonTemporary, snapshot);
            writeCsv(csvTemporary, snapshot);
            publish(jsonTemporary, jsonFile);
            jsonTemporary = null;
            publish(csvTemporary, csvFile);
            csvTemporary = null;
            return directory;
        } catch (IOException | RuntimeException e) {
            LOGGER.error("导出伤害统计失败：{}", directory, e);
            deleteIfExists(jsonTemporary);
            deleteIfExists(csvTemporary);
            deleteIfExists(jsonFile);
            deleteIfExists(csvFile);
            return null;
        }
    }

    private static void writeJson(Path file, StatsSnapshot snapshot) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("exportedAt", LocalDateTime.now().toString());
        root.addProperty("subject", snapshot.subjectName().getString());
        JsonArray filters = new JsonArray();
        snapshot.filterLabels().forEach(label -> filters.add(label.getString()));
        root.add("filterLabels", filters);
        root.add("filter", filterJson(snapshot.appliedFilter()));
        root.add("currentFight", viewJson(snapshot.session()));
        root.add("lifetime", viewJson(snapshot.lifetime()));
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
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            GSON.toJson(root, writer);
        }
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
        object.addProperty("blockedDamage", metrics.totalBlocked());
        object.addProperty("reducedDamage", metrics.reducedDamage());
        object.addProperty("reductionRate", metrics.reductionRate());
        object.add("reductionBreakdown", reductionJson(metrics.reduction()));
        object.addProperty("hits", metrics.hitCount());
        object.addProperty("kills", metrics.killCount());
        object.addProperty("averageHit", metrics.averageDamage());
        object.addProperty("maxHit", metrics.maxSingle());
        object.addProperty("maxOriginalHit", metrics.maxOriginal());
        object.addProperty("maxHitType", metrics.maxSingleTypeName().getString());
        object.add("maxHitTypeId", resourceLocationJson(metrics.maxSingleDamageTypeId()));
        object.add("maxHitDirectSource", entityRefJson(metrics.maxSingleDirectSource()));
        object.addProperty("maxHitDirectSourceName", metrics.maxSingleDirectSourceName().getString());
        object.addProperty("maxHitAt", metrics.maxSingleTime());
        object.addProperty("minHit", metrics.minSingle());
        object.addProperty("averageDps", metrics.averageDps());
        object.addProperty("realtimeDps", metrics.realtimeDps());
        object.addProperty("averageOriginalDps", metrics.averageOriginalDps());
        object.addProperty("realtimeOriginalDps", metrics.realtimeOriginalDps());
        object.addProperty("hitsPerSecond", metrics.hitsPerSecond());
        object.addProperty("durationSeconds", metrics.durationSeconds());
        object.addProperty("durationTicks", metrics.durationTicks());
        return object;
    }

    /** R4.5 的六项减免明细。GUI 里塞不下这么多列，完整数据放在导出里 */
    private static JsonObject reductionJson(DamageReduction reduction) {
        JsonObject object = new JsonObject();
        object.addProperty("armor", reduction.armor());
        object.addProperty("enchantments", reduction.enchantments());
        object.addProperty("mobEffects", reduction.mobEffects());
        object.addProperty("absorption", reduction.absorption());
        object.addProperty("innateResistance", reduction.innateResistance());
        object.addProperty("invulnerability", reduction.invulnerability());
        return object;
    }

    private static JsonArray groupsJson(List<GroupView> groups) {
        JsonArray array = new JsonArray();
        groups.forEach(group -> {
            JsonObject object = new JsonObject();
            object.addProperty("name", group.name().getString());
            object.addProperty("damage", group.damage());
            object.addProperty("originalDamage", group.originalDamage());
            object.addProperty("reducedDamage", Math.max(0, group.originalDamage() - group.damage()));
            object.addProperty("sharePercent", group.share() * 100);
            object.addProperty("hits", group.hitCount());
            object.add("key", filterKeyJson(group.key()));
            array.add(object);
        });
        return array;
    }

    private static JsonObject filterJson(StatsFilter filter) {
        JsonObject object = new JsonObject();
        filter.source().ifPresent(selector -> object.add("source", selectorJson(selector)));
        filter.target().ifPresent(selector -> object.add("target", selectorJson(selector)));
        filter.directSource().ifPresent(selector -> object.add("directSource", selectorJson(selector)));
        filter.damageType().ifPresent(selector -> object.add("damageType", damageTypeSelectorJson(selector)));
        object.addProperty("sourceIsDirectSource", filter.sourceIsDirectSource());
        return object;
    }

    private static JsonObject filterKeyJson(FilterKey key) {
        return switch (key) {
            case FilterKey.Source(EntitySelector selector) -> keyJson("source", selectorJson(selector));
            case FilterKey.Target(EntitySelector selector) -> keyJson("target", selectorJson(selector));
            case FilterKey.Direct(EntitySelector selector) -> keyJson("directSource", selectorJson(selector));
            case FilterKey.Type(DamageTypeSelector selector) -> keyJson("damageType", damageTypeSelectorJson(selector));
        };
    }

    private static JsonObject keyJson(String slot, JsonObject selector) {
        JsonObject object = new JsonObject();
        object.addProperty("slot", slot);
        object.add("selector", selector);
        return object;
    }

    private static JsonObject selectorJson(EntitySelector selector) {
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) -> {
                JsonObject object = new JsonObject();
                object.addProperty("kind", "instance");
                object.addProperty("uuid", ref.id().toString());
                object.addProperty("type", ref.typeIdOrEnvironment().toString());
                yield object;
            }
            case EntitySelector.Type type -> {
                JsonObject object = new JsonObject();
                object.addProperty("kind", "type");
                object.addProperty("type", type.typeId().toString());
                yield object;
            }
        };
    }

    private static JsonObject damageTypeSelectorJson(DamageTypeSelector selector) {
        return switch (selector) {
            case DamageTypeSelector.Category category -> {
                JsonObject object = new JsonObject();
                object.addProperty("kind", "category");
                object.addProperty("name", category.name());
                yield object;
            }
            case DamageTypeSelector.Exact exact -> {
                JsonObject object = new JsonObject();
                object.addProperty("kind", "exact");
                object.addProperty("id", exact.id().toString());
                yield object;
            }
        };
    }

    private static JsonElement resourceLocationJson(@Nullable ResourceLocation location) {
        return location == null ? JsonNull.INSTANCE : new JsonPrimitive(location.toString());
    }

    private static JsonElement entityRefJson(@Nullable EntityRef ref) {
        if(ref == null) return JsonNull.INSTANCE;
        JsonObject object = new JsonObject();
        object.addProperty("uuid", ref.id().toString());
        object.addProperty("type", ref.typeIdOrEnvironment().toString());
        return object;
    }

    private static void writeCsv(Path file, StatsSnapshot snapshot) throws IOException {
        List<String> lines = new ArrayList<>();
        // 添加 UTF-8 BOM，避免 Windows Excel 按本地旧编码解析中文。
        lines.add("\uFEFF" + CSV_HEADER);
        appendScope(lines, "currentFight", snapshot.session());
        appendScope(lines, "lifetime", snapshot.lifetime());
        Files.write(file, lines, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private static void appendScope(List<String> lines, String scope, StatsView view) {
        appendRows(lines, scope, "damageType", view.byType());
        appendRows(lines, scope, "directSource", view.bySource());
        appendRows(lines, scope, "opponent", view.byOpponent());
    }

    private static void appendRows(List<String> lines, String scope,
                                   String dimension, List<GroupView> groups) {
        groups.forEach(group -> lines.add(String.join(",",
                scope,
                dimension,
                csvField(group.name().getString()),
                number(group.damage()),
                number(group.originalDamage()),
                number(Math.max(0, group.originalDamage() - group.damage())),
                number(group.share() * 100),
                String.valueOf(group.hitCount()))));
    }

    /** 实体可能被命名牌起了带逗号或引号的名字，按 CSV 的规矩转义掉 */
    private static String csvField(String value) {
        if(value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    /** 固定 Locale.US，否则小数点会跟随系统语言变成逗号，把 CSV 撑破 */
    private static String number(float value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String nextBaseName(Path directory, String stamp) {
        String baseName = "damagestats-" + stamp;
        int suffix = 1;
        while(Files.exists(directory.resolve(baseName + ".json"))
                || Files.exists(directory.resolve(baseName + ".csv"))) {
            baseName = "damagestats-" + stamp + "-" + suffix++;
        }
        return baseName;
    }

    private static void publish(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, target);
        }
    }

    private static void deleteIfExists(@Nullable Path path) {
        if(path == null) return;
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOGGER.warn("清理导出临时文件失败：{}", path, e);
        }
    }
}
