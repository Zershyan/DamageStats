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
    private static final String Modid = DamageStats.MODID;
    private static final String MessagePrefix = "message." + Modid + ".";
    private static final String StatsPrefix = "stats." + Modid + ".";
    private static final String CategoryPrefix = StatsPrefix + "category.";
    private static final String ScreenPrefix = "screen." + Modid + ".";
    private static final String KeyPrefix = "key." + Modid + ".";

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

    // 指令提示
    public static final MutableComponent NoData = entry(MessagePrefix + "no_data",
            "No damage recorded yet", "还没有记录到伤害数据");
    public static final MutableComponent StatsReset = entry(MessagePrefix + "stats_reset",
            "Damage stats cleared", "伤害统计已清空");
    public static final MutableComponent CategoriesReloaded = entry(MessagePrefix + "categories_reloaded",
            "Damage type categories reloaded", "伤害类型分类配置已重载");
    public static final MutableComponent TrackingDisabled = entry(MessagePrefix + "tracking_disabled",
            "Damage tracking is currently off", "伤害采集当前处于关闭状态");
    public static final MutableComponent TrackingOn = entry(MessagePrefix + "tracking_on",
            "Damage tracking enabled", "已开启伤害采集");
    public static final MutableComponent TrackingOff = entry(MessagePrefix + "tracking_off",
            "Damage tracking disabled", "已关闭伤害采集");
    public static final LazyComponent NoDataForType = entryLazy(MessagePrefix + "no_data_for_type",
            "No data recorded for %s", "%s 还没有统计数据");
    public static final LazyComponent ExportDone = entryLazy(MessagePrefix + "export_done",
            "Exported to %s", "已导出到 %s");
    public static final MutableComponent ExportFailed = entry(MessagePrefix + "export_failed",
            "Export failed, see the log for details", "导出失败，详情见日志");
    public static final MutableComponent StatsPrivate = entry(MessagePrefix + "stats_private",
            "These statistics are private", "统计数据当前为私有");

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
    public static final LazyComponent TypeSummary = entryLazy(StatsPrefix + "type_summary",
            "Type Summary: %s", "类型汇总：%s");
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

    // 按键。entryString 一步完成「登记翻译」和「拿到键名」，KeyMapping 直接用这些常量，不必再抄一遍字符串
    public static final String KeyCategoryId = entryString("key.categories." + Modid,
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
