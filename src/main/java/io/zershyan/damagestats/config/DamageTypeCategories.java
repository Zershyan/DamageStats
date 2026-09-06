package io.zershyan.damagestats.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 伤害类型的分类映射。配置文件按「分类 → 该分类下的伤害类型列表」组织，
 * 分类名是英文标识符，显示时才翻译。没有被归类的伤害类型回落显示原始注册表 ID，
 * 于是第三方 mod 新增的类型即使没人配也不会从统计里消失。
 */
public final class DamageTypeCategories {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "damagestats-damage-types.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, List<String>>>() {}.getType();

    /** 结构和配置文件保持一致，便于对照排查 */
    private static final Map<String, List<ResourceLocation>> CATEGORIES = new LinkedHashMap<>();

    /** 由 CATEGORIES 派生的反向索引，查询走这里，省去每次遍历 */
    private static final Map<ResourceLocation, String> BY_TYPE = new HashMap<>();

    public static void load() {
        Path file = FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
        if(!Files.exists(file)) writeDefaults(file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, List<String>> loaded = GSON.fromJson(reader, FILE_TYPE);
            Map<String, List<ResourceLocation>> categories = new LinkedHashMap<>();
            Map<ResourceLocation, String> byType = new HashMap<>();
            if(loaded != null) {
                loaded.forEach((category, typeIds) -> putCategory(categories, byType, category, typeIds));
            }
            CATEGORIES.clear();
            BY_TYPE.clear();
            CATEGORIES.putAll(categories);
            BY_TYPE.putAll(byType);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("读取伤害类型分类配置失败，保留上一份有效配置。删掉 {} 可在下次启动时重新生成默认内容", FILE_NAME, e);
        }
    }

    /** 玩家自己加的分类没有对应翻译，回落显示分类名本身 */
    public static Component displayName(ResourceLocation damageTypeId) {
        String category = BY_TYPE.get(damageTypeId);
        if(category == null) return Component.literal(damageTypeId.toString());
        return categoryName(category);
    }

    public static Component categoryName(String category) {
        return Component.translatableWithFallback(DSKeyLang.categoryKey(category), category);
    }

    /** 没归类的返回 null，筛选时按「不属于任何分类」处理 */
    public static @Nullable String categoryOf(ResourceLocation damageTypeId) {
        return BY_TYPE.get(damageTypeId);
    }

    public static boolean hasCategory(String category) {
        return CATEGORIES.containsKey(category);
    }

    private static void putCategory(Map<String, List<ResourceLocation>> categories,
                                    Map<ResourceLocation, String> byType,
                                    String category, List<String> typeIds) {
        List<ResourceLocation> parsed = typeIds.stream()
                .map(ResourceLocation::tryParse)
                .filter(Objects::nonNull)
                .toList();
        categories.put(category, parsed);
        parsed.forEach(typeId -> {
            String previous = byType.put(typeId, category);
            if(previous != null) LOGGER.warn("伤害类型 {} 同时被归入 {} 和 {}，按后者算", typeId, previous, category);
        });
    }

    private static void writeDefaults(Path file) {
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(defaults(), writer);
        } catch (IOException e) {
            LOGGER.error("写出伤害类型分类默认配置失败", e);
        }
    }

    /** 只覆盖原版最常见的几种，主要作用是给玩家一份能照着改的样板 */
    private static Map<String, List<String>> defaults() {
        Map<String, List<String>> defaults = new LinkedHashMap<>();
        defaults.put("Physical", List.of(
                "minecraft:player_attack",
                "minecraft:mob_attack",
                "minecraft:arrow",
                "minecraft:trident",
                "minecraft:thorns"));
        defaults.put("Magic", List.of(
                "minecraft:magic",
                "minecraft:indirect_magic",
                "minecraft:wither"));
        defaults.put("Fire", List.of(
                "minecraft:in_fire",
                "minecraft:on_fire",
                "minecraft:lava"));
        defaults.put("Explosion", List.of(
                "minecraft:explosion",
                "minecraft:player_explosion"));
        defaults.put("Environment", List.of(
                "minecraft:fall",
                "minecraft:drown",
                "minecraft:starve",
                "minecraft:freeze"));
        return defaults;
    }
}
