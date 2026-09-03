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
    public static final ConfigEntry InstanceEntryLimit = entry("instanceEntryLimit",
            "Instance Entry Limit", "实例条目上限");
    public static final ConfigEntry KeepFinishedSessions = entry("keepFinishedSessions",
            "Kept Finished Sessions", "保留的已结束会话数");

    // client
    public static final ConfigEntry OverlayVisible = entry("overlayVisible",
            "Show Overlay", "显示常显浮层");
    public static final ConfigEntry OverlayX = entry("overlayX",
            "Overlay X Position", "浮层横向位置");
    public static final ConfigEntry OverlayY = entry("overlayY",
            "Overlay Y Position", "浮层纵向位置");

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
