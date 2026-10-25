package io.zershyan.damagestats.client.ui;

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
    private float focusAmount;

    public UiTextField(Font font, UiLayout.Rect bounds, Component hint) {
        super(font, bounds.x() + PADDING_X, bounds.y() + (bounds.height() - TEXT_HEIGHT) / 2,
                Math.max(1, bounds.width() - PADDING_X * 2), TEXT_HEIGHT, hint);
        this.outerHeight = bounds.height();
        setBordered(false);
        // 1.21.1 的 EditBox 有阴影开关；1.20.1 没有，那边只能整个重写绘制
        setTextShadow(false);
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
        if(getValue().isEmpty() && !isFocused()) {
            UiDraw.funnel(graphics, visual.x() + PADDING_X + 3.5F, visual.centerY(), 8F, 1F,
                    UiTheme.TEXT_DISABLED);
        }
        super.renderWidget(graphics, mouseX, mouseY, partialTick);
        graphics.flush();
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
