package io.zershyan.damagestats.datagen.init;

import io.zershyan.damagestats.DamageStats;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置项的翻译。配置在 toml 里的键名和它的翻译键都从这里取，两边不会写歪。
 * 用 init(List) 返回值风格，因为这些常量要在 DSConfig 的静态初始化阶段就被引用。
 */
public class DSConfigLang extends DSLang {
    private static final List<ConfigEntry> ConfigEntries = new ArrayList<>();

    public record ConfigEntry(String name, Lang lang) {
        public String getKey() {
            return DamageStats.MODID + ".configuration." + name;
        }
    }

    // common
    public static final ConfigEntry TrackingEnabled = entry("trackingEnabled",
            "Enable Damage Tracking", "启用伤害采集");
    public static final ConfigEntry SessionTimeoutTicks = entry("sessionTimeoutTicks",
            "Combat Session Timeout", "战斗会话超时");
    public static final ConfigEntry DpsWindowTicks = entry("dpsWindowTicks",
            "Live DPS Window", "实时 DPS 滑动窗口");
    public static final ConfigEntry InstanceDirectoryRetentionHours = entry("instanceDirectoryRetentionHours",
            "Instance Directory Retention (Hours)", "实例目录保留时长（小时）");
    public static final ConfigEntry InstanceDirectoryLimit = entry("instanceDirectoryLimit",
            "Instance Directory Limit", "实例目录容量上限");
    public static final ConfigEntry KeepFinishedSessions = entry("keepFinishedSessions",
            "Kept Finished Sessions", "保留的已结束会话数");
    public static final ConfigEntry AutoSave = entry("autoSave",
            "Auto Save Statistics", "自动保存统计数据");
    public static final ConfigEntry AutoSaveIntervalTicks = entry("autoSaveIntervalTicks",
            "Auto Save Interval", "自动保存间隔");
    public static final ConfigEntry PublicStats = entry("publicStats",
            "Public Statistics", "统计数据对所有人公开");

    // client
    public static final ConfigEntry OverlayVisible = entry("overlayVisible",
            "Show Overlay", "显示常显浮层");
    public static final ConfigEntry OverlayX = entry("overlayX",
            "Overlay X Position", "浮层横向位置");
    public static final ConfigEntry OverlayY = entry("overlayY",
            "Overlay Y Position", "浮层纵向位置");
    public static final ConfigEntry OverlayScale = entry("overlayScale",
            "Overlay Scale", "浮层缩放");
    public static final ConfigEntry OverlayBackgroundOpacity = entry("overlayBackgroundOpacity",
            "Overlay Background Opacity", "浮层背景透明度");
    public static final ConfigEntry OverlayShowHits = entry("overlayShowHits",
            "Show Overlay Hits", "显示浮层命中次数");
    public static final ConfigEntry OverlayShowFocus = entry("overlayShowFocus",
            "Show Overlay Source and Target", "显示浮层来源与目标");
    public static final ConfigEntry OverlayShowActualDamage = entry("overlayShowActualDamage",
            "Show Final Damage", "显示最终伤害");
    public static final ConfigEntry OverlayActualDamageScope = entry("overlayActualDamageScope",
            "Final Damage Scope", "最终伤害范围");
    public static final ConfigEntry OverlayShowOriginalDamage = entry("overlayShowOriginalDamage",
            "Show Original Damage", "显示原始伤害");
    public static final ConfigEntry OverlayOriginalDamageScope = entry("overlayOriginalDamageScope",
            "Original Damage Scope", "原始伤害范围");
    public static final ConfigEntry OverlayShowReduction = entry("overlayShowReduction",
            "Show Damage Reduction", "显示减免伤害与减伤率");
    public static final ConfigEntry OverlayReductionScope = entry("overlayReductionScope",
            "Damage Reduction Scope", "减免伤害范围");
    public static final ConfigEntry OverlayShowActualAverageDps = entry("overlayShowActualAverageDps",
            "Show Final Average DPS", "显示最终平均 DPS");
    public static final ConfigEntry OverlayActualAverageDpsScope = entry("overlayActualAverageDpsScope",
            "Final Average DPS Scope", "最终平均 DPS 范围");
    public static final ConfigEntry OverlayShowActualRealtimeDps = entry("overlayShowActualRealtimeDps",
            "Show Final Live DPS", "显示最终实时 DPS");
    public static final ConfigEntry OverlayActualRealtimeDpsScope = entry("overlayActualRealtimeDpsScope",
            "Final Live DPS Scope", "最终实时 DPS 范围");
    public static final ConfigEntry OverlayShowOriginalAverageDps = entry("overlayShowOriginalAverageDps",
            "Show Original Average DPS", "显示原始平均 DPS");
    public static final ConfigEntry OverlayOriginalAverageDpsScope = entry("overlayOriginalAverageDpsScope",
            "Original Average DPS Scope", "原始平均 DPS 范围");
    public static final ConfigEntry OverlayShowOriginalRealtimeDps = entry("overlayShowOriginalRealtimeDps",
            "Show Original Live DPS", "显示原始实时 DPS");
    public static final ConfigEntry OverlayOriginalRealtimeDpsScope = entry("overlayOriginalRealtimeDpsScope",
            "Original Live DPS Scope", "原始实时 DPS 范围");
    public static final ConfigEntry OverlayShowAverageHit = entry("overlayShowAverageHit",
            "Show Average Hit", "显示平均单次伤害");
    public static final ConfigEntry OverlayAverageHitScope = entry("overlayAverageHitScope",
            "Average Hit Scope", "平均单次伤害范围");
    public static final ConfigEntry OverlayHitsScope = entry("overlayHitsScope",
            "Hit Count Scope", "命中次数范围");
    public static final ConfigEntry OverlayShowMaxOriginal = entry("overlayShowMaxOriginal",
            "Show Highest Original Damage", "显示最高原始伤害");
    public static final ConfigEntry OverlayMaxOriginalScope = entry("overlayMaxOriginalScope",
            "Highest Original Damage Scope", "最高原始伤害范围");
    public static final ConfigEntry OverlayShowMaxActual = entry("overlayShowMaxActual",
            "Show Highest Final Damage", "显示最高最终伤害");
    public static final ConfigEntry OverlayMaxActualScope = entry("overlayMaxActualScope",
            "Highest Final Damage Scope", "最高最终伤害范围");
    public static final ConfigEntry OverlayShowTopDamageType = entry("overlayShowTopDamageType",
            "Show Top Damage Type", "显示最高贡献伤害类型");
    public static final ConfigEntry OverlayTopDamageTypeScope = entry("overlayTopDamageTypeScope",
            "Top Damage Type Scope", "最高贡献伤害类型范围");
    public static final ConfigEntry OverlayShowTopDirectSource = entry("overlayShowTopDirectSource",
            "Show Top Direct Source", "显示最高贡献直接来源");
    public static final ConfigEntry OverlayTopDirectSourceScope = entry("overlayTopDirectSourceScope",
            "Top Direct Source Scope", "最高贡献直接来源范围");
    public static final ConfigEntry OverlayShowSessionStatus = entry("overlayShowSessionStatus",
            "Show Combat Session State", "显示战斗会话状态");

    private static ConfigEntry entry(String name, String enUs, String zhCn) {
        ConfigEntry configEntry = new ConfigEntry(name, new Lang(enUs, zhCn));
        ConfigEntries.add(configEntry);
        return configEntry;
    }

    @Override
    public List<Entry> init(List<Entry> entries) {
        ConfigEntries.forEach(config -> entries.add(new FinalEntry<>(config.getKey(), config.lang())));
        return entries;
    }
}
