package io.zershyan.damagestats.client.ui;

import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * 圆角搜索框。控件本体只占中间那一条文字区域，外框由自己向外扩张绘制——
 * 原版 {@code EditBox} 关闭 bordered 后文字固定画在控件左上角，把控件本身缩到文字行才能居中。
 */
public class UiTextField extends EditBox {
    private static final int PADDING_X = 6;
    private static final int TEXT_HEIGHT = 8;

    private final int outerHeight;
    private final Component hintText;
    private float focusAmount;
    /** 自己维护的水平滚动位置，让光标始终留在可见区内 */
    private int displayOffset;

    public UiTextField(Font font, UiLayout.Rect bounds, Component hint) {
        super(font, bounds.x() + PADDING_X, bounds.y() + (bounds.height() - TEXT_HEIGHT) / 2,
                Math.max(1, bounds.width() - PADDING_X * 2), TEXT_HEIGHT, hint);
        this.outerHeight = bounds.height();
        this.hintText = hint;
        setBordered(false);
        setHint(hint);
        setTextColor(UiTheme.TEXT);
        setTextColorUneditable(UiTheme.TEXT_MUTED);
    }

    /**
     * 外框按控件当前位置反推，而不是记构造时那份坐标——
     * 控件被 setY 搬过之后，记下来的那份会让框和文字错位，看着像「点不到」。
     */
    private UiLayout.Rect visual() {
        int insetY = (outerHeight - TEXT_HEIGHT) / 2;
        return new UiLayout.Rect(getX() - PADDING_X, getY() - insetY, getWidth() + PADDING_X * 2, outerHeight);
    }

    /**
     * 只借 {@link EditBox} 的输入逻辑，绘制全部自己来。
     * 1.20.1 的原版 EditBox 内部把 drawString 的 dropShadow 写死成 true（连字段都没有），
     * 没有开关能关掉——想让搜索框不出现阴影就只能自己画文字。
     */
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        focusAmount = UiTheme.approach(focusAmount, isFocused() ? 1F : 0F, UiTheme.ANIM_FAST, UiTheme.delta());
        float focus = UiTheme.easeOut(focusAmount);
        UiLayout.Rect visual = visual();
        float radius = UiTheme.RADIUS_MD;
        // 线性搜索框：实色底面，焦点时描边从 BORDER 过渡到 ACCENT，不加外光圈
        UiDraw.roundedRect(graphics, visual.x(), visual.y(), visual.width(), visual.height(), radius, UiTheme.SURFACE);
        UiDraw.roundedBorder(graphics, visual.x() + 0.5F, visual.y() + 0.5F, visual.width() - 1F,
                visual.height() - 1F, Math.max(1F, radius - 0.5F), 1F,
                UiTheme.mix(UiTheme.BORDER, UiTheme.ACCENT, focus));
        renderValue(graphics);
        graphics.flush();
    }

    private void renderValue(GuiGraphics graphics) {
        Font font = UiTheme.font();
        int left = getX();
        int textY = getY();
        String value = getValue();
        if(value.isEmpty()) {
            if(isFocused()) return;
            // 空且未聚焦时给个放大镜 + 提示，替代原版那个带阴影的 hint
            UiDraw.funnel(graphics, left + 3.5F, textY + TEXT_HEIGHT / 2F, 8F, 1F, UiTheme.TEXT_DISABLED);
            UiText.plain(graphics, UiText.truncate(hintText, Math.max(1, getWidth() - 12)), left + 11, textY,
                    UiTheme.TEXT_DISABLED);
            return;
        }
        int cursor = getCursorPosition();
        int visibleWidth = Math.max(1, getWidth() - (isFocused() ? 5 : 0));
        // 滚动到光标可见
        if(cursor < displayOffset) displayOffset = cursor;
        while(displayOffset < cursor && font.width(value.substring(displayOffset, cursor)) > visibleWidth) {
            displayOffset++;
        }
        displayOffset = Math.min(displayOffset, value.length());
        String shown = font.plainSubstrByWidth(value.substring(displayOffset), visibleWidth);
        String highlighted = getHighlighted();
        if(highlighted != null && !highlighted.isEmpty()) {
            int highlightWidth = Math.min(font.width(highlighted), visibleWidth);
            int highlightLeft = Math.max(left, left + font.width(value.substring(displayOffset, cursor)) - highlightWidth);
            UiDraw.rect(graphics, highlightLeft, textY - 1F, highlightWidth, TEXT_HEIGHT + 1F, UiTheme.ACCENT_SOFT);
        }
        UiText.plain(graphics, Component.literal(shown), left, textY, UiTheme.TEXT);
        if(isFocused() && (Util.getMillis() / 500L) % 2L == 0L) {
            int cursorX = Math.min(left + visibleWidth, left + font.width(value.substring(displayOffset, cursor)));
            UiDraw.rect(graphics, cursorX, textY - 1F, 1F, TEXT_HEIGHT + 1F, UiTheme.ACCENT_BRIGHT);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visual().contains(mouseX, mouseY);
    }

    @Override
    public boolean clicked(double mouseX, double mouseY) {
        return this.active && this.visible && visual().contains(mouseX, mouseY);
    }
}
