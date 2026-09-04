package io.zershyan.damagestats.config;

import io.zershyan.damagestats.datagen.init.DSConfigLang;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 配置项。显示名走 {@link DSConfigLang} 的翻译键，comment 只留技术性说明（单位、取值含义），
 * 两者不重复。配置键名同样取自 DSConfigLang，改名只需改一处。
 */
public final class DSConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TrackingEnabled = BUILDER
            .comment("关闭后不再记录新的伤害，已经统计到的数据仍然保留")
            .translation(DSConfigLang.TrackingEnabled.getKey())
            .define(DSConfigLang.TrackingEnabled.name(), true);

    public static final ModConfigSpec.IntValue SessionTimeoutTicks = BUILDER
            .comment("单位 tick，20 tick = 1 秒。超过这个时长没有新伤害就结束本场战斗会话")
            .translation(DSConfigLang.SessionTimeoutTicks.getKey())
            .defineInRange(DSConfigLang.SessionTimeoutTicks.name(), 100, 20, 12000);

    public static final ModConfigSpec.IntValue DpsWindowTicks = BUILDER
            .comment("单位 tick。窗口越短越能反映瞬时爆发，越长越平滑")
            .translation(DSConfigLang.DpsWindowTicks.getKey())
            .defineInRange(DSConfigLang.DpsWindowTicks.name(), 100, 20, 1200);

    public static final ModConfigSpec.IntValue InstanceDirectoryRetentionHours = BUILDER
            .comment("单位现实小时。到期后仅从实例候选目录移除，不删除完整原始伤害事件")
            .translation(DSConfigLang.InstanceDirectoryRetentionHours.getKey())
            .defineInRange(DSConfigLang.InstanceDirectoryRetentionHours.name(), 72, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue InstanceDirectoryLimit = BUILDER
            .comment("单位每世界候选实例数。超出后淘汰最久未交互的实例，不删除完整原始伤害事件")
            .translation(DSConfigLang.InstanceDirectoryLimit.getKey())
            .defineInRange(DSConfigLang.InstanceDirectoryLimit.name(), 5000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue KeepFinishedSessions = BUILDER
            .comment("设为 0 则只保留当前会话，不留历史")
            .translation(DSConfigLang.KeepFinishedSessions.getKey())
            .defineInRange(DSConfigLang.KeepFinishedSessions.name(), 10, 0, 100);

    public static final ModConfigSpec.BooleanValue AutoSave = BUILDER
            .comment("是否按 autoSaveIntervalTicks 定时写出聚合缓存。完整事件和实例候选会在正常停止时始终保存")
            .translation(DSConfigLang.AutoSave.getKey())
            .define(DSConfigLang.AutoSave.name(), true);

    public static final ModConfigSpec.IntValue AutoSaveIntervalTicks = BUILDER
            .comment("单位 tick。正常退出和崩服都会写盘，这个只防被强制结束进程（kill -9、断电）。0 表示不定时写盘")
            .translation(DSConfigLang.AutoSaveIntervalTicks.getKey())
            .defineInRange(DSConfigLang.AutoSaveIntervalTicks.name(), 0, 0, 432000);

    public static final ModConfigSpec.BooleanValue PublicStats = BUILDER
            .comment("允许玩家查看其他实体的统计。因为目标的承伤明细里带着其他玩家的输出，关掉后只有管理员能查")
            .translation(DSConfigLang.PublicStats.getKey())
            .define(DSConfigLang.PublicStats.name(), true);

    /** 只在注册配置时用一次，不属于「会被反复阅读的配置项」，因此保持全大写 */
    public static final ModConfigSpec SPEC = BUILDER.build();
}
