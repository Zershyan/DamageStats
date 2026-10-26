package io.zershyan.damagestats.client.ui;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * 文字排版。MC 只有一套 9px 位图字体，既没有字号也没有真正的重字重，只能靠「加粗 + 颜色深浅」
 * 拉开层次，所以加粗要克制：只给标题、分组名和主数值用。
 * <p>
 * 加粗一律交给原版的 {@link ChatFormatting#BOLD}。原版绘制粗体时会把每个字形多画一遍，**同时按同样的
 * 偏移把字距也撑开**，所以相邻字不会互相压住；早先那种「整串文字再画一遍、偏移 1px」的写法字距没跟着动，
 * 每个字的副本正好落进下一个字的格子里，CJK 连成一片，看起来就是一层脏重影。
 */
public final class UiText {
    /** 加粗过的组件。原版渲染器据此撑开字距，宽度也要用它来量。 */
    public static Component bolded(Component text) {
        return text.copy().withStyle(ChatFormatting.BOLD);
    }

    public static void bold(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(UiTheme.font(), bolded(text), x, y, color, false);
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

    /** 折行后的文本是 FormattedCharSequence，用带 dropShadow 参数的重载同样能去掉阴影。 */
    public static void centered(GuiGraphics graphics, FormattedCharSequence text, int centerX, int y,
                                int color) {
        Font font = UiTheme.font();
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, false);
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

    /** 加粗后的实际宽度。原版会给每个字多留 1px 字距，不能拿普通宽度去估。 */
    public static int boldWidth(Component text) {
        return UiTheme.font().width(bolded(text));
    }

    /** 按宽度截断，超出部分用省略号收尾。 */
    public static Component truncate(Component text, int maxWidth) {
        Font font = UiTheme.font();
        if(maxWidth <= 0) return Component.empty();
        if(font.width(text) <= maxWidth) return text;
        return Component.literal(font.plainSubstrByWidth(text.getString(),
                Math.max(1, maxWidth - font.width("…"))) + "…");
    }

    /** 加粗文本的截断：加粗会加宽每个字，必须用加粗后的宽度去量，否则末尾会被挤出边界。 */
    public static Component truncateBold(Component text, int maxWidth) {
        Font font = UiTheme.font();
        if(maxWidth <= 0) return Component.empty();
        if(font.width(bolded(text)) <= maxWidth) return text;
        String plain = text.getString();
        int keep = plain.length();
        while(keep > 0 && font.width(bolded(Component.literal(plain.substring(0, keep) + "…"))) > maxWidth) {
            keep--;
        }
        return Component.literal(plain.substring(0, keep) + "…");
    }

    /** 按宽度折行，用于弹窗说明这类需要成段的文本。 */
    public static List<FormattedCharSequence> wrap(Component text, int maxWidth) {
        return UiTheme.font().split(text, Math.max(1, maxWidth));
    }

    public static int lineHeight() {
        return UiTheme.font().lineHeight;
    }
}
