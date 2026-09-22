package io.zershyan.damagestats.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 文字排版。MC 只有一套 9px 位图字体，既没有字号也没有字重——网页那套排版层次在这里拿不到，
 * 只能用「伪粗体 + 颜色深浅」两把武器，所以粗细的用法必须克制：只给标题、分组名和主数值加粗。
 */
public final class UiText {
    /** 伪粗体的水平偏移量。 */
    private static final int BOLD_OFFSET = 1;

    /** 伪粗体：同一串文字向右多画一遍，字形被撑宽 1px，观感接近 semibold。 */
    public static void bold(GuiGraphics graphics, Component text, int x, int y, int color) {
        Font font = UiTheme.font();
        graphics.drawString(font, text, x, y, color, false);
        graphics.drawString(font, text, x + BOLD_OFFSET, y, color, false);
    }

    public static void plain(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(UiTheme.font(), text, x, y, color, false);
    }

    public static void boldCentered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        bold(graphics, text, centerX - boldWidth(text) / 2, y, color);
    }

    public static void centered(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        plain(graphics, text, centerX - UiTheme.font().width(text) / 2, y, color);
    }

    /** 右对齐到 rightX（不含加粗多出来的那 1px）。 */
    public static void boldRight(GuiGraphics graphics, Component text, int rightX, int y, int color) {
        bold(graphics, text, rightX - boldWidth(text), y, color);
    }

    public static void right(GuiGraphics graphics, Component text, int rightX, int y, int color) {
        plain(graphics, text, rightX - UiTheme.font().width(text), y, color);
    }

    public static int width(Component text) {
        return UiTheme.font().width(text);
    }

    public static int boldWidth(Component text) {
        return UiTheme.font().width(text) + BOLD_OFFSET;
    }

    /** 按宽度截断，超出部分用省略号收尾。 */
    public static Component truncate(Component text, int maxWidth) {
        Font font = UiTheme.font();
        if(maxWidth <= 0) return Component.empty();
        if(font.width(text) <= maxWidth) return text;
        return Component.literal(font.plainSubstrByWidth(text.getString(),
                Math.max(1, maxWidth - font.width("…"))) + "…");
    }

    /** 加粗文本要预留右边多出来的 1px，否则最后一笔会被裁掉。 */
    public static Component truncateBold(Component text, int maxWidth) {
        return truncate(text, maxWidth - BOLD_OFFSET);
    }

    /** 按宽度折行，用于弹窗说明这类需要成段的文本。 */
    public static List<FormattedCharSequence> wrap(Component text, int maxWidth) {
        return UiTheme.font().split(text, Math.max(1, maxWidth));
    }

    public static int lineHeight() {
        return UiTheme.font().lineHeight;
    }
}
