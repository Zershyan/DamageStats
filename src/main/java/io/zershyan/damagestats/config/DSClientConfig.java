package io.zershyan.damagestats.config;

import io.zershyan.damagestats.datagen.init.DSConfigLang;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端专属配置。浮层位置存的是占屏幕宽高的比例而不是像素，
 * 于是改分辨率或切窗口大小之后浮层不会跑到屏幕外面去。
 */
public final class DSClientConfig {
    /** 默认位置提成常量，位置编辑界面的「重置」按钮要用同一份值 */
    public static final double DefaultOverlayX = 0.01;
    public static final double DefaultOverlayY = 0.3;
    public static final double DefaultOverlayScale = 1.0;
    public static final double DefaultOverlayBackgroundOpacity = 0.5;

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue OverlayVisible = BUILDER
            .comment("常显浮层是否显示。也可以用按键随时开关，开关状态会存回这里")
            .translation(DSConfigLang.OverlayVisible.getKey())
            .define(DSConfigLang.OverlayVisible.name(), false);

    public static final ForgeConfigSpec.DoubleValue OverlayX = BUILDER
            .comment("浮层左上角的横向位置，0 是屏幕最左，1 是最右")
            .translation(DSConfigLang.OverlayX.getKey())
            .defineInRange(DSConfigLang.OverlayX.name(), DefaultOverlayX, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue OverlayY = BUILDER
            .comment("浮层左上角的纵向位置，0 是屏幕最上，1 是最下")
            .translation(DSConfigLang.OverlayY.getKey())
            .defineInRange(DSConfigLang.OverlayY.name(), DefaultOverlayY, 0.0, 1.0);

    public static final ForgeConfigSpec.DoubleValue OverlayScale = BUILDER
            .comment("常显浮层的缩放比例")
            .translation(DSConfigLang.OverlayScale.getKey())
            .defineInRange(DSConfigLang.OverlayScale.name(), DefaultOverlayScale, 0.5, 2.0);

    public static final ForgeConfigSpec.DoubleValue OverlayBackgroundOpacity = BUILDER
            .comment("常显浮层背景的不透明度，0 为完全透明")
            .translation(DSConfigLang.OverlayBackgroundOpacity.getKey())
            .defineInRange(DSConfigLang.OverlayBackgroundOpacity.name(), DefaultOverlayBackgroundOpacity, 0.0, 1.0);

    public static final ForgeConfigSpec.BooleanValue OverlayShowHits = BUILDER
            .comment("常显浮层是否显示命中次数")
            .translation(DSConfigLang.OverlayShowHits.getKey())
            .define(DSConfigLang.OverlayShowHits.name(), true);

    public static final ForgeConfigSpec.BooleanValue OverlayShowFocus = bool(DSConfigLang.OverlayShowFocus, true);
    public static final ForgeConfigSpec.BooleanValue OverlayShowActualDamage = bool(DSConfigLang.OverlayShowActualDamage, true);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayActualDamageScope = scope(DSConfigLang.OverlayActualDamageScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowOriginalDamage = bool(DSConfigLang.OverlayShowOriginalDamage, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayOriginalDamageScope = scope(DSConfigLang.OverlayOriginalDamageScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowReduction = bool(DSConfigLang.OverlayShowReduction, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayReductionScope = scope(DSConfigLang.OverlayReductionScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowActualAverageDps = bool(DSConfigLang.OverlayShowActualAverageDps, true);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayActualAverageDpsScope = scope(DSConfigLang.OverlayActualAverageDpsScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowActualRealtimeDps = bool(DSConfigLang.OverlayShowActualRealtimeDps, true);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayActualRealtimeDpsScope = scope(DSConfigLang.OverlayActualRealtimeDpsScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowOriginalAverageDps = bool(DSConfigLang.OverlayShowOriginalAverageDps, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayOriginalAverageDpsScope = scope(DSConfigLang.OverlayOriginalAverageDpsScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowOriginalRealtimeDps = bool(DSConfigLang.OverlayShowOriginalRealtimeDps, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayOriginalRealtimeDpsScope = scope(DSConfigLang.OverlayOriginalRealtimeDpsScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowAverageHit = bool(DSConfigLang.OverlayShowAverageHit, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayAverageHitScope = scope(DSConfigLang.OverlayAverageHitScope);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayHitsScope = scope(DSConfigLang.OverlayHitsScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowMaxOriginal = bool(DSConfigLang.OverlayShowMaxOriginal, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayMaxOriginalScope = scope(DSConfigLang.OverlayMaxOriginalScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowMaxActual = bool(DSConfigLang.OverlayShowMaxActual, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayMaxActualScope = scope(DSConfigLang.OverlayMaxActualScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowTopDamageType = bool(DSConfigLang.OverlayShowTopDamageType, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayTopDamageTypeScope = scope(DSConfigLang.OverlayTopDamageTypeScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowTopDirectSource = bool(DSConfigLang.OverlayShowTopDirectSource, false);
    public static final ForgeConfigSpec.EnumValue<OverlayMetricScope> OverlayTopDirectSourceScope = scope(DSConfigLang.OverlayTopDirectSourceScope);
    public static final ForgeConfigSpec.BooleanValue OverlayShowSessionStatus = bool(DSConfigLang.OverlayShowSessionStatus, false);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static ForgeConfigSpec.BooleanValue bool(DSConfigLang.ConfigEntry entry, boolean defaultValue) {
        return BUILDER.translation(entry.getKey()).define(entry.name(), defaultValue);
    }

    private static ForgeConfigSpec.EnumValue<OverlayMetricScope> scope(DSConfigLang.ConfigEntry entry) {
        return BUILDER.translation(entry.getKey()).defineEnum(entry.name(), OverlayMetricScope.SESSION);
    }
}
