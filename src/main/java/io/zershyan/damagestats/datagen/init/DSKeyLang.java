package io.zershyan.damagestats.datagen.init;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.util.LazyComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 声明一条翻译 = 写一个 public static final，登记多语言和拿到 Component 一步完成 */
public class DSKeyLang extends DSLang {
    private static final List<FinalEntry<String>> TranslatableLang = new ArrayList<>();
    private static final String ModId = DamageStats.MODID;
    private static final String ModName = DamageStats.class.getSimpleName();
    private static final String MessagePrefix = "message." + ModId + ".";
    private static final String StatsPrefix = "stats." + ModId + ".";
    private static final String CategoryPrefix = StatsPrefix + "category.";
    private static final String ScreenPrefix = "screen." + ModId + ".";
    private static final String KeyPrefix = "key." + ModId + ".";

    private static String entryString(String key, String enUs, String zhCn) {
        TranslatableLang.add(new FinalEntry<>(key, enUs, zhCn));
        return key;
    }

    private static MutableComponent entry(String key, String enUs, String zhCn) {
        return Component.translatable(entryString(key, enUs, zhCn));
    }

    private static LazyComponent entryLazy(String key, String enUs, String zhCn) {
        return new LazyComponent(entryString(key, enUs, zhCn));
    }

    public static final MutableComponent Resource = entry(ModId + ".resources", "Resources for " + ModName, ModName + "资源");

    // 指令提示
    public static final MutableComponent NoData = entry(MessagePrefix + "no_data",
            "No damage recorded yet", "还没有记录到伤害数据");
    public static final MutableComponent CategoriesReloaded = entry(MessagePrefix + "categories_reloaded",
            "Damage type categories reloaded", "伤害类型分类配置已重载");
    public static final MutableComponent TrackingOn = entry(MessagePrefix + "tracking_on",
            "Damage tracking enabled", "已开启伤害采集");
    public static final MutableComponent TrackingOff = entry(MessagePrefix + "tracking_off",
            "Damage tracking disabled", "已关闭伤害采集");
    public static final LazyComponent ExportDone = entryLazy(MessagePrefix + "export_done",
            "Exported to %s", "已导出到 %s");
    public static final MutableComponent ExportFailed = entry(MessagePrefix + "export_failed",
            "Export failed, see the log for details", "导出失败，详情见日志");
    public static final MutableComponent ResetFailed = entry(MessagePrefix + "reset_failed",
            "Statistics reset could not be persisted", "统计清理未能持久化");
    public static final MutableComponent StatsPrivate = entry(MessagePrefix + "stats_private",
            "These statistics are private", "统计数据当前为私有");
    public static final LazyComponent StorageCleanupDone = entryLazy(MessagePrefix + "storage_cleanup_done",
            "Storage cleanup completed: %s", "存储清理已完成：%s");
    public static final LazyComponent StorageCleanupFailed = entryLazy(MessagePrefix + "storage_cleanup_failed",
            "Storage cleanup failed: %s", "存储清理失败：%s");

    // 区块标题
    public static final MutableComponent TitleOutgoing = entry(StatsPrefix + "title.outgoing",
            "Damage Dealt", "造成的伤害");
    public static final MutableComponent TitleIncoming = entry(StatsPrefix + "title.incoming",
            "Damage Taken", "承受的伤害");
    public static final MutableComponent SectionSession = entry(StatsPrefix + "section.session",
            "Current Fight", "本场战斗");
    public static final MutableComponent SectionLifetime = entry(StatsPrefix + "section.lifetime",
            "Lifetime Total", "存档累计");
    public static final MutableComponent SectionTypes = entry(StatsPrefix + "section.types",
            "By Damage Type", "按伤害类型");
    public static final MutableComponent SectionSources = entry(StatsPrefix + "section.sources",
            "By Direct Source", "按直接来源");
    public static final MutableComponent SectionOpponents = entry(StatsPrefix + "section.opponents",
            "By Opponent", "按对手");
    public static final MutableComponent SectionHistory = entry(StatsPrefix + "section.history",
            "Finished Fights", "已结束的战斗");
    public static final MutableComponent SourceEnvironment = entry(StatsPrefix + "source.environment",
            "Environment", "环境");

    // 指标
    public static final LazyComponent TotalDamage = entryLazy(StatsPrefix + "total_damage",
            "Total Damage: %s", "总伤害：%s");
    public static final LazyComponent OriginalDamage = entryLazy(StatsPrefix + "original_damage",
            "Before Reduction: %s", "减免前伤害：%s");
    public static final LazyComponent ReductionRate = entryLazy(StatsPrefix + "reduction_rate",
            "Reduced: %s (%s%%)", "被减免：%s（%s%%）");
    public static final LazyComponent HitCount = entryLazy(StatsPrefix + "hit_count",
            "Hits: %s", "命中次数：%s");
    public static final LazyComponent AverageDamage = entryLazy(StatsPrefix + "average_damage",
            "Average Hit: %s", "平均单次：%s");
    public static final LazyComponent MaxSingle = entryLazy(StatsPrefix + "max_single",
            "Highest Hit: %s (%s, via %s, at tick %s)", "最高单次：%s（%s，直接来源：%s，时刻：%s）");
    public static final LazyComponent MinSingle = entryLazy(StatsPrefix + "min_single",
            "Lowest Hit: %s", "最低单次：%s");
    public static final LazyComponent HitsPerSecond = entryLazy(StatsPrefix + "hits_per_second",
            "Hits/s: %s", "每秒命中：%s");
    public static final LazyComponent AverageDps = entryLazy(StatsPrefix + "average_dps",
            "Average DPS: %s", "本场平均 DPS：%s");
    public static final LazyComponent RealtimeDps = entryLazy(StatsPrefix + "realtime_dps",
            "Live DPS: %s", "实时 DPS：%s");
    public static final LazyComponent Duration = entryLazy(StatsPrefix + "duration",
            "Duration: %ss", "统计时长：%s 秒");
    public static final LazyComponent KillCount = entryLazy(StatsPrefix + "kill_count",
            "Kills: %s", "击杀数：%s");
    public static final LazyComponent DetailLine = entryLazy(StatsPrefix + "detail_line",
            "%s: %s (%s%%) x%s", "%s：%s（%s%%）× %s 次");
    public static final LazyComponent SessionLine = entryLazy(StatsPrefix + "session_line",
            "Fight %s: %s damage, %s DPS, %ss, %s hits", "第 %s 场：伤害 %s，DPS %s，时长 %s 秒，命中 %s 次");
    public static final LazyComponent InstanceLastInteraction = entryLazy(StatsPrefix + "instance.last_interaction",
            "Last interaction: tick %s at %s", "最近交互：游戏刻 %s，位置 %s");
    public static final LazyComponent InstanceQueryStats = entryLazy(StatsPrefix + "instance.query_stats",
            "Current query: %s damage, %s hits", "当前查询：伤害 %s，命中 %s 次");
    public static final LazyComponent ReductionArmor = entryLazy(StatsPrefix + "reduction.armor",
            "Armor: %s", "护甲减免：%s");
    public static final LazyComponent ReductionEnchantments = entryLazy(StatsPrefix + "reduction.enchantments",
            "Enchantments: %s", "附魔减免：%s");
    public static final LazyComponent ReductionMobEffects = entryLazy(StatsPrefix + "reduction.mob_effects",
            "Effects: %s", "药水效果减免：%s");
    public static final LazyComponent ReductionAbsorption = entryLazy(StatsPrefix + "reduction.absorption",
            "Absorption: %s", "伤害吸收：%s");
    public static final LazyComponent ReductionInnateResistance = entryLazy(StatsPrefix + "reduction.innate_resistance",
            "Innate Resistance: %s", "天生抗性减免：%s");
    public static final LazyComponent ReductionInvulnerability = entryLazy(StatsPrefix + "reduction.invulnerability",
            "Invulnerability: %s", "无敌帧减免：%s");

    // 按键。entryString 一步完成「登记翻译」和「拿到键名」，KeyMapping 直接用这些常量，不必再抄一遍字符串
    public static final String KeyCategoryId = entryString("key.categories." + ModId,
            "DamageStats", "伤害统计");
    public static final String OpenGuiKeyId = entryString(KeyPrefix + "open_gui",
            "Open Damage Stats", "打开伤害统计");
    public static final String ToggleOverlayKeyId = entryString(KeyPrefix + "toggle_overlay",
            "Toggle Overlay", "开关常显浮层");

    // Overlay
    public static final MutableComponent OverlayAllTargets = entry(StatsPrefix + "overlay.all_targets",
            "All Targets", "全部目标");
    public static final LazyComponent OverlayDamage = entryLazy(StatsPrefix + "overlay.damage",
            "DMG %s", "伤害 %s");
    public static final LazyComponent OverlayDps = entryLazy(StatsPrefix + "overlay.dps",
            "DPS %s / %s", "DPS %s / %s");
    public static final LazyComponent OverlayHits = entryLazy(StatsPrefix + "overlay.hits",
            "Hits %s", "命中 %s");
    public static final LazyComponent OverlayFocus = entryLazy(StatsPrefix + "overlay.focus",
            "%s -> %s", "%s -> %s");
    public static final LazyComponent OverlayFinalDamage = entryLazy(StatsPrefix + "overlay.final_damage",
            "Final Damage: %s", "最终伤害：%s");
    public static final LazyComponent OverlayOriginalDamage = entryLazy(StatsPrefix + "overlay.original_damage",
            "Original Damage: %s", "原始伤害：%s");
    public static final LazyComponent OverlayReduction = entryLazy(StatsPrefix + "overlay.reduction",
            "Reduced: %s (%s%%)", "减免：%s（%s%%）");
    public static final LazyComponent OverlayFinalAverageDps = entryLazy(StatsPrefix + "overlay.final_average_dps",
            "Final Average DPS: %s", "最终平均 DPS：%s");
    public static final LazyComponent OverlayFinalRealtimeDps = entryLazy(StatsPrefix + "overlay.final_realtime_dps",
            "Final Live DPS: %s", "最终实时 DPS：%s");
    public static final LazyComponent OverlayOriginalAverageDps = entryLazy(StatsPrefix + "overlay.original_average_dps",
            "Original Average DPS: %s", "原始平均 DPS：%s");
    public static final LazyComponent OverlayOriginalRealtimeDps = entryLazy(StatsPrefix + "overlay.original_realtime_dps",
            "Original Live DPS: %s", "原始实时 DPS：%s");
    public static final LazyComponent OverlayAverageHit = entryLazy(StatsPrefix + "overlay.average_hit",
            "Average Hit: %s", "平均单次：%s");
    public static final LazyComponent OverlayHighestOriginal = entryLazy(StatsPrefix + "overlay.highest_original",
            "Highest Original: %s", "最高原始伤害：%s");
    public static final LazyComponent OverlayHighestFinal = entryLazy(StatsPrefix + "overlay.highest_final",
            "Highest Final: %s", "最高最终伤害：%s");
    public static final LazyComponent OverlayHighestBoth = entryLazy(StatsPrefix + "overlay.highest_both",
            "Highest Damage: %s / %s", "最高伤害：%s / %s");
    public static final LazyComponent OverlayTopDamageType = entryLazy(StatsPrefix + "overlay.top_damage_type",
            "Top Damage Type: %s (%s, %s%%)", "最高贡献伤害类型：%s（%s，%s%%）");
    public static final LazyComponent OverlayTopDirectSource = entryLazy(StatsPrefix + "overlay.top_direct_source",
            "Top Direct Source: %s (%s, %s%%)", "最高贡献直接来源：%s（%s，%s%%）");
    public static final LazyComponent OverlaySessionActive = entryLazy(StatsPrefix + "overlay.session_active",
            "Fight Active: %ss", "本场进行中：%s 秒");
    public static final MutableComponent OverlaySessionInactive = entry(StatsPrefix + "overlay.session_inactive",
            "Fight Inactive", "本场未进行");
    public static final MutableComponent OverlayScopeSession = entry(StatsPrefix + "overlay.scope_session",
            "Fight", "本场");
    public static final MutableComponent OverlayScopeLifetime = entry(StatsPrefix + "overlay.scope_lifetime",
            "Total", "累计");
    public static final MutableComponent OverlayShown = entry(MessagePrefix + "overlay_shown",
            "Overlay shown", "已显示常显浮层");
    public static final MutableComponent OverlayHidden = entry(MessagePrefix + "overlay_hidden",
            "Overlay hidden", "已隐藏常显浮层");

    // 位置编辑
    public static final MutableComponent EditPositionTitle = entry(ScreenPrefix + "edit_position.title",
            "Drag the overlay to reposition it", "把浮层拖到想要的位置");
    public static final MutableComponent EditPositionDone = entry(ScreenPrefix + "edit_position.done",
            "Done", "完成");
    public static final MutableComponent EditPositionReset = entry(ScreenPrefix + "edit_position.reset",
            "Reset Position", "重置位置");
    public static final MutableComponent EditPositionScale = entry(ScreenPrefix + "edit_position.scale",
            "Scale", "缩放");
    public static final MutableComponent EditPositionOpacity = entry(ScreenPrefix + "edit_position.opacity",
            "Background Opacity", "背景透明度");
    public static final MutableComponent EditPositionTarget = entry(ScreenPrefix + "edit_position.target",
            "Target", "目标");
    public static final MutableComponent EditPositionDamage = entry(ScreenPrefix + "edit_position.damage",
            "Damage", "伤害");
    public static final MutableComponent EditPositionDps = entry(ScreenPrefix + "edit_position.dps",
            "DPS", "DPS");
    public static final MutableComponent EditPositionHits = entry(ScreenPrefix + "edit_position.hits",
            "Hits", "命中次数");

    // 统计界面
    public static final MutableComponent ScreenTitle = entry(ScreenPrefix + "title",
            "Damage Stats", "伤害统计");
    public static final LazyComponent ScreenTitleOf = entryLazy(ScreenPrefix + "title_of",
            "Damage Stats — %s", "伤害统计 — %s");
    public static final MutableComponent ViewSelf = entry(ScreenPrefix + "view.self",
            "Me", "我");
    public static final MutableComponent ViewAll = entry(ScreenPrefix + "view.all",
            "Everyone", "全部");
    public static final LazyComponent ViewType = entryLazy(ScreenPrefix + "view.type",
            "Type: %s", "类型：%s");
    public static final MutableComponent TabOutgoing = entry(ScreenPrefix + "tab.outgoing",
            "Dealt", "造成");
    public static final MutableComponent TabIncoming = entry(ScreenPrefix + "tab.incoming",
            "Taken", "承受");
    public static final MutableComponent ScopeSession = entry(ScreenPrefix + "scope.session",
            "This Fight", "本场");
    public static final MutableComponent ScopeLifetime = entry(ScreenPrefix + "scope.lifetime",
            "Total", "累计");
    public static final MutableComponent DimensionTypes = entry(ScreenPrefix + "dimension.types",
            "Types", "伤害类型");
    public static final MutableComponent DimensionSources = entry(ScreenPrefix + "dimension.sources",
            "Sources", "直接来源");
    public static final MutableComponent DimensionOpponents = entry(ScreenPrefix + "dimension.opponents",
            "Opponents", "对手");
    public static final MutableComponent ScreenGroupingCategory = entry(ScreenPrefix + "grouping.category",
            "Categories", "分类");
    public static final MutableComponent ScreenGroupingRegistry = entry(ScreenPrefix + "grouping.registry",
            "Registry IDs", "注册表 ID");
    public static final MutableComponent ScreenDetails = entry(ScreenPrefix + "details",
            "Details", "详细指标");
    public static final MutableComponent ScreenHistory = entry(ScreenPrefix + "history",
            "History", "历史战斗");
    public static final MutableComponent ScreenEditOverlay = entry(ScreenPrefix + "edit_overlay",
            "Overlay Position", "浮层位置");
    public static final MutableComponent ScreenExport = entry(ScreenPrefix + "export",
            "Export", "导出");
    public static final MutableComponent ScreenReset = entry(ScreenPrefix + "reset",
            "Reset Mine", "清空我的");
    public static final MutableComponent ScreenResetAll = entry(ScreenPrefix + "reset_all",
            "Reset All", "清空全部");
    public static final MutableComponent ScreenClearFilter = entry(ScreenPrefix + "clear_filter",
            "Clear Filter", "清除筛选");
    public static final MutableComponent ScreenAllDamage = entry(ScreenPrefix + "all_damage",
            "All Damage", "全部伤害");
    public static final MutableComponent ScreenTruncated = entry(ScreenPrefix + "truncated",
            "(incomplete)", "（数据不完整）");
    public static final LazyComponent ScreenChooseTypeAction = entryLazy(ScreenPrefix + "choose_type_action",
            "Use %s as", "将 %s 用作");
    public static final MutableComponent ScreenSetSubject = entry(ScreenPrefix + "set_subject",
            "Set as Subject", "设为主体");
    public static final MutableComponent ScreenSetOtherFilter = entry(ScreenPrefix + "set_other_filter",
            "Set as Other Filter", "设为另一筛选项");
    public static final MutableComponent ScreenRefresh = entry(ScreenPrefix + "refresh",
            "Refresh", "刷新");
    public static final MutableComponent ScreenSearch = entry(ScreenPrefix + "search",
            "Search", "搜索");
    public static final MutableComponent ScreenLoading = entry(ScreenPrefix + "loading",
            "Loading...", "正在加载……");
    public static final MutableComponent ScreenRecent = entry(ScreenPrefix + "recent",
            "Recent types", "最近类型");
    public static final LazyComponent ScreenSort = entryLazy(ScreenPrefix + "sort",
            "Sort: %s", "排序：%s");
    public static final MutableComponent SortRecent = entry(ScreenPrefix + "sort.recent",
            "Recent", "最近");
    public static final MutableComponent SortDamage = entry(ScreenPrefix + "sort.damage",
            "Highest damage", "最高伤害");
    public static final MutableComponent SortHits = entry(ScreenPrefix + "sort.hits",
            "Most hits", "最多命中");
    public static final MutableComponent ScreenPrevious = entry(ScreenPrefix + "previous",
            "Previous", "上一页");
    public static final MutableComponent ScreenNext = entry(ScreenPrefix + "next",
            "Next", "下一页");
    public static final MutableComponent ScreenInstances = entry(ScreenPrefix + "instances",
            "Instances", "查看实例");
    public static final MutableComponent ScreenSelect = entry(ScreenPrefix + "select",
            "Select", "选择");
    public static final MutableComponent ScreenSetFocus = entry(ScreenPrefix + "set_focus",
            "Set Focus", "设为焦点");
    public static final MutableComponent ScreenClearTarget = entry(ScreenPrefix + "clear_target",
            "Clear Target", "清除目标");
    public static final MutableComponent ScreenClearSource = entry(ScreenPrefix + "clear_source",
            "Clear Source", "清除来源");
    public static final MutableComponent ScreenClearChartFilters = entry(ScreenPrefix + "clear_chart_filters",
            "Clear Chart Filters", "清除图表筛选");
    public static final MutableComponent ScreenCurrentFocus = entry(ScreenPrefix + "current_focus",
            "Current Focus", "当前焦点");
    public static final MutableComponent ScreenBrowsing = entry(ScreenPrefix + "browsing",
            "Browsing", "正在浏览");
    public static final MutableComponent ScreenConfirmReset = entry(ScreenPrefix + "confirm_reset",
            "Clear My Statistics?", "确认清空我的统计？");
    public static final MutableComponent ScreenConfirmResetAll = entry(ScreenPrefix + "confirm_reset_all",
            "Clear All Statistics?", "确认清空全部统计？");
    public static final MutableComponent ScreenResetMineDescription = entry(ScreenPrefix + "reset_mine_description",
            "Only your visible statistics will be cleared.", "仅清空你可见的个人统计数据。");
    public static final MutableComponent ScreenResetAllDescription = entry(ScreenPrefix + "reset_all_description",
            "This clears every player's statistics in this world.", "这会清空当前世界中所有玩家的统计数据。");
    public static final MutableComponent ScreenConfirm = entry(ScreenPrefix + "confirm",
            "Confirm", "确认");
    public static final MutableComponent ScreenActions = entry(ScreenPrefix + "actions",
            "More Actions", "更多操作");
    public static final MutableComponent ScreenStorageTitle = entry(ScreenPrefix + "storage.title",
            "Server Storage", "服务器存储");
    public static final MutableComponent ScreenStorageRefresh = entry(ScreenPrefix + "storage.refresh",
            "Refresh", "刷新");
    public static final MutableComponent ScreenStorageCleanupTemporary = entry(ScreenPrefix + "storage.cleanup_temporary",
            "Clean Temporary Files and Backups", "清理临时文件和迁移备份");
    public static final MutableComponent ScreenStorageCleanupExports = entry(ScreenPrefix + "storage.cleanup_exports",
            "Clean Server Exports", "清理服务器导出文件");
    public static final MutableComponent ScreenStorageCleanupAll = entry(ScreenPrefix + "storage.cleanup_all",
            "Clear All Records", "清理全部记录");
    public static final MutableComponent ScreenStorageConfirmTitle = entry(ScreenPrefix + "storage.confirm_title",
            "Confirm Storage Cleanup", "确认清理存储");
    public static final MutableComponent ScreenStorageConfirmTemporary = entry(ScreenPrefix + "storage.confirm_temporary",
            "Only temporary files and migration backups will be removed.", "仅会删除临时文件和迁移备份。");
    public static final MutableComponent ScreenStorageConfirmExports = entry(ScreenPrefix + "storage.confirm_exports",
            "Only DamageStats export files in the server world will be removed.", "仅会删除服务器世界目录中的 DamageStats 导出文件。");
    public static final MutableComponent ScreenStorageConfirmAll = entry(ScreenPrefix + "storage.confirm_all",
            "All damage records in this world will be permanently cleared.", "当前世界中的全部伤害记录将被永久清空。");
    public static final LazyComponent ScreenStorageTotal = entryLazy(ScreenPrefix + "storage.total",
            "Total Usage: %s", "总占用：%s");
    public static final LazyComponent ScreenStorageEvents = entryLazy(ScreenPrefix + "storage.events",
            "Events: %s", "事件数：%s");
    public static final LazyComponent ScreenStorageSegments = entryLazy(ScreenPrefix + "storage.segments",
            "Segments: %s", "分段数：%s");
    public static final LazyComponent ScreenStorageIndex = entryLazy(ScreenPrefix + "storage.index",
            "Index: %s", "索引状态：%s");
    public static final LazyComponent ScreenStorageRawSegments = entryLazy(ScreenPrefix + "storage.raw_segments",
            "Raw Segments: %s", "原始段占用：%s");
    public static final LazyComponent ScreenStorageCompressedSegments = entryLazy(ScreenPrefix + "storage.compressed_segments",
            "Compressed Segments: %s", "压缩段占用：%s");
    public static final LazyComponent ScreenStorageIndexBytes = entryLazy(ScreenPrefix + "storage.index_bytes",
            "Index Files: %s", "索引文件占用：%s");
    public static final LazyComponent ScreenStorageCache = entryLazy(ScreenPrefix + "storage.cache",
            "Aggregate Cache: %s", "聚合缓存占用：%s");
    public static final LazyComponent ScreenStorageExports = entryLazy(ScreenPrefix + "storage.exports",
            "Server Exports: %s", "服务器导出占用：%s");
    public static final LazyComponent ScreenStorageTemporary = entryLazy(ScreenPrefix + "storage.temporary",
            "Temporary Files: %s", "临时文件占用：%s");
    public static final LazyComponent ScreenStorageBackups = entryLazy(ScreenPrefix + "storage.backups",
            "Migration Backups: %s", "迁移备份占用：%s");
    public static final MutableComponent ScreenStorageIndexEmpty = entry(ScreenPrefix + "storage.index_empty",
            "Empty", "为空");
    public static final MutableComponent ScreenStorageIndexLoaded = entry(ScreenPrefix + "storage.index_loaded",
            "Loaded", "已加载");
    public static final MutableComponent ScreenStorageIndexRebuilt = entry(ScreenPrefix + "storage.index_rebuilt",
            "Rebuilt", "已重建");
    public static final MutableComponent ScreenStorageIndexFailed = entry(ScreenPrefix + "storage.index_failed",
            "Unavailable", "不可用");
    public static final MutableComponent ScreenFilter = entry(ScreenPrefix + "filter",
            "Filter", "筛选");
    public static final MutableComponent ScreenSetAsSource = entry(ScreenPrefix + "set_as_source",
            "Set as Source", "设为来源");
    public static final MutableComponent ScreenSetAsTarget = entry(ScreenPrefix + "set_as_target",
            "Set as Target", "设为目标");
    public static final MutableComponent ScreenSetAsDirectSource = entry(ScreenPrefix + "set_as_direct_source",
            "Set as Direct Source", "设为直接来源");
    public static final MutableComponent ScreenSetFocusSource = entry(ScreenPrefix + "set_focus_source",
            "Set Focus Source", "设为焦点来源");
    public static final MutableComponent ScreenSetFocusTarget = entry(ScreenPrefix + "set_focus_target",
            "Set Focus Target", "设为焦点目标");
    public static final LazyComponent FilterSource = entryLazy(ScreenPrefix + "filter.source",
            "From: %s", "来源：%s");
    public static final LazyComponent FilterTarget = entryLazy(ScreenPrefix + "filter.target",
            "To: %s", "目标：%s");
    public static final LazyComponent FilterDirect = entryLazy(ScreenPrefix + "filter.direct",
            "Via: %s", "直接来源：%s");
    public static final LazyComponent FilterType = entryLazy(ScreenPrefix + "filter.type",
            "Type: %s", "伤害类型：%s");

    // 伤害分类。这几个常量只负责登记翻译，取用走 categoryKey 派生的动态键
    public static final MutableComponent CategoryPhysical = entry(categoryKey("Physical"), "Physical", "物理伤害");
    public static final MutableComponent CategoryMagic = entry(categoryKey("Magic"), "Magic", "魔法伤害");
    public static final MutableComponent CategoryFire = entry(categoryKey("Fire"), "Fire", "火焰伤害");
    public static final MutableComponent CategoryExplosion = entry(categoryKey("Explosion"), "Explosion", "爆炸伤害");
    public static final MutableComponent CategoryEnvironment = entry(categoryKey("Environment"), "Environment", "环境伤害");

    /** 分类的翻译键统一由这里生成，登记和查询共用一份逻辑，不会对不上 */
    public static String categoryKey(String category) {
        return CategoryPrefix + category.toLowerCase(Locale.ROOT);
    }

    @Override
    public List<Entry> init(List<Entry> entries) {
        entries.addAll(TranslatableLang);
        return entries;
    }
}
