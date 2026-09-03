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

    public static final ModConfigSpec.IntValue InstanceEntryLimit = BUILDER
            .comment("超出后淘汰最久没有交互的条目，玩家的条目不参与淘汰")
            .translation(DSConfigLang.InstanceEntryLimit.getKey())
            .defineInRange(DSConfigLang.InstanceEntryLimit.name(), 50, 10, 1000);

    public static final ModConfigSpec.IntValue KeepFinishedSessions = BUILDER
            .comment("设为 0 则只保留当前会话，不留历史")
            .translation(DSConfigLang.KeepFinishedSessions.getKey())
            .defineInRange(DSConfigLang.KeepFinishedSessions.name(), 10, 0, 100);

    public static final ModConfigSpec.BooleanValue AutoSave = BUILDER
            .comment("退出世界时把统计写进存档目录，下次进同一个存档接着往上累计。关掉则每次进游戏都从零开始")
            .translation(DSConfigLang.AutoSave.getKey())
            .define(DSConfigLang.AutoSave.name(), true);

    /** 只在注册配置时用一次，不属于「会被反复阅读的配置项」，因此保持全大写 */
    public static final ModConfigSpec SPEC = BUILDER.build();
}
