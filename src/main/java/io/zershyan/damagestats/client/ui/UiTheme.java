package io.zershyan.damagestats.client.ui;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;

/** 全局设计令牌：颜色、圆角、间距、动效时长。所有界面只从这里取值，保证观感统一。 */
public final class UiTheme {
    /**
     * 玻璃层。alpha 是这套观感的核心：面板越透，后面被虚化的世界越明显。
     * 想把玻璃调浓/调淡，只改下面这几个 alpha 就够了，其余颜色都从它们派生。
     */
    /**
     * 1.20.1 没有背景模糊后处理，面板后面透出来的是清晰的世界方块，半透明只会显脏。
     * 所以这一支把 alpha 调到接近不透明，玻璃感改由阴影、内高光和顶部光照渐变来做。
     */
    public static final int SCRIM = 0xC8000000;
    public static final int BACKDROP = 0xF00C1016;
    public static final int BACKDROP_TOP = 0xF2111823;
    public static final int SURFACE = 0xF2151B23;
    public static final int SURFACE_RAISED = 0xF51B232C;
    public static final int SURFACE_HOVER = 0xFA222B36;
    public static final int CHROME = 0xF50F141B;
    /** 弹出层浮在所有内容之上，必须够实，否则底下的文字会透上来干扰阅读 */
    public static final int OVERLAY = 0xFB1B232C;

    // 描边与内高光
    public static final int BORDER = 0xFF232C36;
    public static final int BORDER_STRONG = 0xFF323E4B;
    public static final int HIGHLIGHT = 0x12FFFFFF;
    public static final int SHADOW = 0x8C000000;

    // 文字
    public static final int TEXT = 0xFFE6EDF3;
    public static final int TEXT_STRONG = 0xFFFFFFFF;
    public static final int TEXT_SECONDARY = 0xFF9DAEBF;
    public static final int TEXT_MUTED = 0xFF68788A;
    public static final int TEXT_DISABLED = 0xFF434E5A;

    // 强调色
    public static final int ACCENT = 0xFF3FC7E0;
    public static final int ACCENT_BRIGHT = 0xFF74E2F5;
    public static final int ACCENT_SOFT = 0x333FC7E0;
    public static final int ACCENT_FAINT = 0x1A3FC7E0;
    public static final int ON_ACCENT = 0xFF06141A;

    // 语义色
    public static final int DANGER = 0xFFE0575B;
    public static final int DANGER_BRIGHT = 0xFFF07F82;
    public static final int DANGER_SOFT = 0x2EE0575B;
    public static final int WARNING = 0xFFE8B04B;

    // 图表
    public static final int BAR_TRACK = 0x14FFFFFF;
    public static final int BAR_TOP = 0xFF3BA8C4;
    public static final int BAR_BOTTOM = 0xFF25758D;
    public static final int BAR_HOVER_TOP = 0xFF5CDDF5;
    public static final int BAR_HOVER_BOTTOM = 0xFF35A6C4;
    public static final int BAR_CHILD_TOP = 0xFF4A5768;
    public static final int BAR_CHILD_BOTTOM = 0xFF333D4A;
    public static final int ROW_HOVER = 0x1AFFFFFF;

    // 圆角
    public static final int RADIUS_SM = 4;
    public static final int RADIUS_MD = 6;
    public static final int RADIUS_LG = 9;

    // 动效（秒）
    public static final float ANIM_FAST = 0.10F;
    public static final float ANIM_BASE = 0.16F;

    private static long lastFrameMillis = Util.getMillis();

    /** 每个界面在 render 开头调用一次，刷新共享帧间隔；控件据此推进 hover 动画。 */
    public static void tick() {
        long now = Util.getMillis();
        deltaSeconds = Mth.clamp((now - lastFrameMillis) / 1000F, 0F, 0.1F);
        lastFrameMillis = now;
    }

    private static float deltaSeconds = 0.016F;

    public static float delta() {
        return deltaSeconds;
    }

    /** 界面统一取默认字体，控件里不用再各自拿 Minecraft 实例。 */
    public static Font font() {
        return Minecraft.getInstance().font;
    }

    /** 按帧间隔把当前值匀速推向目标值；步长受限所以不会过冲，可安全用于任意区间的插值。 */
    public static float approach(float current, float target, float speed, float deltaSeconds) {
        float step = Math.max(0F, deltaSeconds) / Math.max(0.001F, speed);
        return current + Mth.clamp(target - current, -step, step);
    }

    /** 缓出曲线，让 hover 动画收尾更自然。 */
    public static float easeOut(float t) {
        float clamped = Mth.clamp(t, 0F, 1F);
        return 1F - (1F - clamped) * (1F - clamped);
    }

    /** 按比例混合两个 ARGB 颜色，比例 0 取前色、1 取后色。 */
    public static int mix(int from, int to, float ratio) {
        float t = Mth.clamp(ratio, 0F, 1F);
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 只改透明度，保留原色。 */
    public static int alpha(int color, float alphaScale) {
        int a = Math.round(((color >>> 24) & 0xFF) * Mth.clamp(alphaScale, 0F, 1F));
        return (a << 24) | (color & 0x00FFFFFF);
    }

    /** 在给定色上叠加一层同色高光，用于悬停态提亮。 */
    public static int lighten(int color, float amount) {
        return mix(color, 0xFFFFFFFF, amount);
    }
}
