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

    public static final ModConfigSpec.BooleanValue OverlayShowTarget = BUILDER
            .comment("常显浮层是否显示目标")
            .translation(DSConfigLang.OverlayShowTarget.getKey())
            .define(DSConfigLang.OverlayShowTarget.name(), true);

    public static final ModConfigSpec.BooleanValue OverlayShowDamage = BUILDER
            .comment("常显浮层是否显示总伤害")
            .translation(DSConfigLang.OverlayShowDamage.getKey())
            .define(DSConfigLang.OverlayShowDamage.name(), true);

    public static final ModConfigSpec.BooleanValue OverlayShowDps = BUILDER
            .comment("常显浮层是否显示 DPS")
            .translation(DSConfigLang.OverlayShowDps.getKey())
            .define(DSConfigLang.OverlayShowDps.name(), true);

    public static final ModConfigSpec.BooleanValue OverlayShowHits = BUILDER
            .comment("常显浮层是否显示命中次数")
            .translation(DSConfigLang.OverlayShowHits.getKey())
            .define(DSConfigLang.OverlayShowHits.name(), true);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
