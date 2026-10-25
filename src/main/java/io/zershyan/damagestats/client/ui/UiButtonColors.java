package io.zershyan.damagestats.client.ui;

/**
 * 按钮的颜色规则，纯计算，不依赖 Minecraft 类。
 * 游戏里的 {@link UiButton} 把 {@link UiTheme} 的颜色传进来；离线预览图传入同一组颜色，
 * 所以审核的按钮颜色与游戏里的计算完全一致。
 *
 * 颜色规则：每个变体的四态（静止、悬停、按下、禁用）各取一个实色，不做渐变与透明叠加。
 * 静止、悬停、按下三者的明度互不相同，所以交互反馈清楚。
 */
public final class UiButtonColors {
    private UiButtonColors() {}

    /** 按钮所需的基础色，全部由 UiTheme 提供。 */
    public record Palette(int accent, int accentBright, int onAccent, int icon,
                          int surface, int surfaceRaised, int surfaceHover, int backdrop,
                          int danger, int dangerBright, int dangerSoft,
                          int borderStrong, int textStrong, int textSecondary, int textMuted,
                          int textDisabled) {}

    /** 不透明化：去掉 alpha，保证面是实色。 */
    public static int solid(int color) {
        return 0xFF000000 | (color & 0x00FFFFFF);
    }

    /**
     * 按 ratio 在两个颜色之间逐通道插值，与 {@link UiTheme#mix} 数值一致。
     * 这里自己实现一份，是为了让本类不依赖 UiTheme，从而可以被不带 Minecraft 的预览程序直接编译。
     */
    static int mix(int from, int to, float ratio) {
        float t = Math.max(0F, Math.min(1F, ratio));
        int a = Math.round(((from >>> 24) & 0xFF) + (((to >>> 24) & 0xFF) - ((from >>> 24) & 0xFF)) * t);
        int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /** 底色。透明返回 0。 */
    public static int fill(Palette p, int variant, boolean hovered, boolean pressed) {
        return switch(variant) {
            case 0 -> pressed ? solid(mix(p.accent(), 0xFF000000, 0.18F))
                    : hovered ? p.accentBright() : p.accent();
            case 1 -> pressed ? p.backdrop() : hovered ? p.surfaceHover() : p.surfaceRaised();
            case 2 -> pressed ? p.backdrop() : hovered ? p.surfaceRaised() : 0x00000000;
            case 3 -> pressed ? solid(mix(p.surface(), p.danger(), 0.08F))
                    : hovered ? solid(mix(p.surface(), p.danger(), 0.22F))
                    : solid(mix(p.surface(), p.danger(), 0.14F));
            case 4 -> pressed ? p.backdrop() : hovered ? p.surfaceRaised() : 0x00000000;
            default -> pressed ? solid(mix(p.surface(), p.accent(), 0.07F))
                    : hovered ? solid(mix(p.surface(), p.accent(), 0.20F))
                    : solid(mix(p.surface(), p.accent(), 0.12F));
        };
    }

    /** 描边。不需要描边的变体返回 0。 */
    public static int border(Palette p, int variant, boolean hovered) {
        return switch(variant) {
            case 0 -> 0x00000000;
            case 1 -> hovered ? p.accent() : p.borderStrong();
            case 2 -> 0x00000000;
            case 3 -> hovered ? p.dangerBright() : p.danger();
            case 4 -> hovered ? p.accent() : p.borderStrong();
            default -> p.accent();
        };
    }

    /** 图标颜色。与文字分开取色：文字在幽灵、次要按钮上偏暗，图标需要更高对比度才看得清。 */
    public static int icon(Palette p, int variant) {
        return switch(variant) {
            case 0 -> p.onAccent();
            case 3 -> p.dangerBright();
            default -> p.icon();
        };
    }

    /** 文字颜色。 */
    public static int text(Palette p, int variant, boolean hovered) {
        return switch(variant) {
            case 0 -> p.onAccent();
            case 1 -> hovered ? p.textStrong() : p.textSecondary();
            case 2 -> hovered ? p.textStrong() : p.textMuted();
            case 3 -> hovered ? p.dangerBright() : p.danger();
            case 4 -> hovered ? p.accent() : p.textSecondary();
            default -> p.accentBright();
        };
    }
}
