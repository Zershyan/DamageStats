package io.zershyan.damagestats.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/** 圆角轨道 + 圆形滑块的滑杆，拖动逻辑沿用原版 {@link AbstractSliderButton}。 */
public class UiSlider extends AbstractSliderButton {
    private static final float TRACK_HEIGHT = 4F;
    private static final float THUMB_RADIUS = 5F;

    private final double minimum;
    private final double maximum;
    private final DoubleFunction<Component> labelFor;
    private final DoubleConsumer onValue;

    private float hoverAmount;
    private boolean pressed;

    /**
     * @param bounds   控件区域
     * @param value    初始值，会被夹到 [minimum, maximum]
     * @param minimum  最小值
     * @param maximum  最大值
     * @param labelFor 把当前值转成显示文案
     * @param onValue  值变化时的回调
     */
    public UiSlider(UiLayout.Rect bounds, double value, double minimum, double maximum,
                    DoubleFunction<Component> labelFor, DoubleConsumer onValue) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Component.empty(),
                (Math.clamp(value, minimum, maximum) - minimum) / (maximum - minimum));
        this.minimum = minimum;
        this.maximum = maximum;
        this.labelFor = labelFor;
        this.onValue = onValue;
        updateMessage();
    }

    /** 当前滑杆表示的数值。 */
    public double currentValue() {
        return minimum + value * (maximum - minimum);
    }

    @Override
    protected void updateMessage() {
        setMessage(labelFor.apply(currentValue()));
    }

    @Override
    protected void applyValue() {
        onValue.accept(currentValue());
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float delta = UiTheme.delta();
        boolean emphasized = isHovered || pressed;
        hoverAmount = UiTheme.approach(hoverAmount, emphasized ? 1F : 0F, UiTheme.ANIM_FAST, delta);
        boolean interactive = this.active;

        float trackWidth = Math.max(1F, width - THUMB_RADIUS * 2F);
        float trackX = getX() + THUMB_RADIUS;
        float trackY = getY() + height / 2F - TRACK_HEIGHT / 2F;
        float filled = (float) Math.clamp(value, 0D, 1D) * trackWidth;
        float thumbX = trackX + filled;
        float thumbY = getY() + height / 2F;

        // 线性滑轨：实色底轨加 1px 描边，已填充段用实色强调色，未激活时换成暗青，没有渐变与光晕
        UiDraw.roundedRect(graphics, trackX, trackY, trackWidth, TRACK_HEIGHT, TRACK_HEIGHT / 2F,
                interactive ? UiTheme.SURFACE_RAISED : UiTheme.BACKDROP);
        UiDraw.roundedBorder(graphics, trackX - 0.5F, trackY - 0.5F, trackWidth + 1F, TRACK_HEIGHT + 1F,
                TRACK_HEIGHT / 2F, 1F, UiTheme.BORDER);
        if(filled > 0.5F) {
            UiDraw.roundedRect(graphics, trackX, trackY, filled, TRACK_HEIGHT, TRACK_HEIGHT / 2F,
                    interactive ? UiTheme.ACCENT : UiTheme.ACCENT_SOFT);
        }
        // 滑块：实心圆，悬停或按下时换成更亮的白；中心一个强调色点。不画外圈光晕
        int knob = !interactive ? UiTheme.TEXT_MUTED : emphasized ? UiTheme.TEXT_STRONG : UiTheme.TEXT;
        UiDraw.dot(graphics, thumbX, thumbY, THUMB_RADIUS * 2F, knob);
        UiDraw.dot(graphics, thumbX, thumbY, (THUMB_RADIUS - 2.5F) * 2F,
                interactive ? UiTheme.ACCENT : UiTheme.BACKDROP);

        Font font = UiTheme.font();
        Component label = getMessage();
        int available = Math.max(1, width - (int) (THUMB_RADIUS * 2F) - 6);
        Component display = font.width(label) > available
                ? Component.literal(font.plainSubstrByWidth(label.getString(), Math.max(1, available - 4)) + "…")
                : label;
        int labelColor = interactive ? UiTheme.TEXT : UiTheme.TEXT_DISABLED;
        UiText.centered(graphics, display, getX() + width / 2,
                getY() + (height - font.lineHeight) / 2 + 1, labelColor);
        graphics.flush();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if(handled) pressed = true;
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
