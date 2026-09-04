package io.zershyan.damagestats.config;

import io.zershyan.damagestats.datagen.init.DSConfigLang;
import net.neoforged.neoforge.common.ModConfigSpec;

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

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue OverlayVisible = BUILDER
            .comment("常显浮层是否显示。也可以用按键随时开关，开关状态会存回这里")
            .translation(DSConfigLang.OverlayVisible.getKey())
            .define(DSConfigLang.OverlayVisible.name(), false);

    public static final ModConfigSpec.DoubleValue OverlayX = BUILDER
            .comment("浮层左上角的横向位置，0 是屏幕最左，1 是最右")
            .translation(DSConfigLang.OverlayX.getKey())
            .defineInRange(DSConfigLang.OverlayX.name(), DefaultOverlayX, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue OverlayY = BUILDER
            .comment("浮层左上角的纵向位置，0 是屏幕最上，1 是最下")
            .translation(DSConfigLang.OverlayY.getKey())
            .defineInRange(DSConfigLang.OverlayY.name(), DefaultOverlayY, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue OverlayScale = BUILDER
            .comment("常显浮层的缩放比例")
            .translation(DSConfigLang.OverlayScale.getKey())
            .defineInRange(DSConfigLang.OverlayScale.name(), DefaultOverlayScale, 0.5, 2.0);

    public static final ModConfigSpec.DoubleValue OverlayBackgroundOpacity = BUILDER
            .comment("常显浮层背景的不透明度，0 为完全透明")
            .translation(DSConfigLang.OverlayBackgroundOpacity.getKey())
            .defineInRange(DSConfigLang.OverlayBackgroundOpacity.name(), DefaultOverlayBackgroundOpacity, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue OverlayShowHits = BUILDER
            .comment("常显浮层是否显示命中次数")
            .translation(DSConfigLang.OverlayShowHits.getKey())
            .define(DSConfigLang.OverlayShowHits.name(), true);

    public static final ModConfigSpec.BooleanValue OverlayShowFocus = bool(DSConfigLang.OverlayShowFocus, true);
    public static final ModConfigSpec.BooleanValue OverlayShowActualDamage = bool(DSConfigLang.OverlayShowActualDamage, true);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayActualDamageScope = scope(DSConfigLang.OverlayActualDamageScope);
    public static final ModConfigSpec.BooleanValue OverlayShowOriginalDamage = bool(DSConfigLang.OverlayShowOriginalDamage, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayOriginalDamageScope = scope(DSConfigLang.OverlayOriginalDamageScope);
    public static final ModConfigSpec.BooleanValue OverlayShowReduction = bool(DSConfigLang.OverlayShowReduction, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayReductionScope = scope(DSConfigLang.OverlayReductionScope);
    public static final ModConfigSpec.BooleanValue OverlayShowActualAverageDps = bool(DSConfigLang.OverlayShowActualAverageDps, true);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayActualAverageDpsScope = scope(DSConfigLang.OverlayActualAverageDpsScope);
    public static final ModConfigSpec.BooleanValue OverlayShowActualRealtimeDps = bool(DSConfigLang.OverlayShowActualRealtimeDps, true);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayActualRealtimeDpsScope = scope(DSConfigLang.OverlayActualRealtimeDpsScope);
    public static final ModConfigSpec.BooleanValue OverlayShowOriginalAverageDps = bool(DSConfigLang.OverlayShowOriginalAverageDps, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayOriginalAverageDpsScope = scope(DSConfigLang.OverlayOriginalAverageDpsScope);
    public static final ModConfigSpec.BooleanValue OverlayShowOriginalRealtimeDps = bool(DSConfigLang.OverlayShowOriginalRealtimeDps, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayOriginalRealtimeDpsScope = scope(DSConfigLang.OverlayOriginalRealtimeDpsScope);
    public static final ModConfigSpec.BooleanValue OverlayShowAverageHit = bool(DSConfigLang.OverlayShowAverageHit, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayAverageHitScope = scope(DSConfigLang.OverlayAverageHitScope);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayHitsScope = scope(DSConfigLang.OverlayHitsScope);
    public static final ModConfigSpec.BooleanValue OverlayShowMaxOriginal = bool(DSConfigLang.OverlayShowMaxOriginal, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayMaxOriginalScope = scope(DSConfigLang.OverlayMaxOriginalScope);
    public static final ModConfigSpec.BooleanValue OverlayShowMaxActual = bool(DSConfigLang.OverlayShowMaxActual, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayMaxActualScope = scope(DSConfigLang.OverlayMaxActualScope);
    public static final ModConfigSpec.BooleanValue OverlayShowTopDamageType = bool(DSConfigLang.OverlayShowTopDamageType, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayTopDamageTypeScope = scope(DSConfigLang.OverlayTopDamageTypeScope);
    public static final ModConfigSpec.BooleanValue OverlayShowTopDirectSource = bool(DSConfigLang.OverlayShowTopDirectSource, false);
    public static final ModConfigSpec.EnumValue<OverlayMetricScope> OverlayTopDirectSourceScope = scope(DSConfigLang.OverlayTopDirectSourceScope);
    public static final ModConfigSpec.BooleanValue OverlayShowSessionStatus = bool(DSConfigLang.OverlayShowSessionStatus, false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static ModConfigSpec.BooleanValue bool(DSConfigLang.ConfigEntry entry, boolean defaultValue) {
        return BUILDER.translation(entry.getKey()).define(entry.name(), defaultValue);
    }

    private static ModConfigSpec.EnumValue<OverlayMetricScope> scope(DSConfigLang.ConfigEntry entry) {
        return BUILDER.translation(entry.getKey()).defineEnum(entry.name(), OverlayMetricScope.SESSION);
    }
}
