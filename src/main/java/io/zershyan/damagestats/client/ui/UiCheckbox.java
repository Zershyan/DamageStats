package io.zershyan.damagestats.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/** 勾选框：整行可点，选中时方块填充强调色并淡入一个对勾。 */
public class UiCheckbox extends AbstractWidget implements UiTruncatable {
    private static final int BOX_SIZE = 11;
    private static final int LABEL_GAP = 6;

    private final Consumer<Boolean> onToggle;
    private boolean checked;
    private float hoverAmount;
    private float checkAmount;

    public UiCheckbox(UiLayout.Rect bounds, Component label, boolean checked, Consumer<Boolean> onToggle) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), label);
        this.checked = checked;
        this.onToggle = onToggle;
        this.checkAmount = checked ? 1F : 0F;
    }

    /** 界面每帧同步真实配置值，配置被别处改动时控件不会停留在旧状态。 */
    public void setChecked(boolean value) {
        this.checked = value;
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = UiTheme.font();
        float delta = UiTheme.delta();
        hoverAmount = UiTheme.approach(hoverAmount, isHovered && active ? 1F : 0F, UiTheme.ANIM_FAST, delta);
        checkAmount = UiTheme.approach(checkAmount, checked ? 1F : 0F, UiTheme.ANIM_FAST, delta);
        float hover = UiTheme.easeOut(hoverAmount);
        float mark = UiTheme.easeOut(checkAmount);

        int boxSize = boxSize();
        float boxY = getY() + (height - boxSize) / 2F;
        // 线性方块：未选中是抬高的实色面（悬停提亮），选中是强调色实心，没有渐变
        int fill = mark > 0.5F ? UiTheme.ACCENT
                : hover > 0.5F ? UiTheme.SURFACE_HOVER : UiTheme.SURFACE_RAISED;
        int border = mark > 0.5F ? UiTheme.ACCENT : UiTheme.BORDER_STRONG;
        UiDraw.roundedRect(graphics, getX(), boxY, boxSize, boxSize, UiTheme.RADIUS_SM,
                active ? fill : UiTheme.BACKDROP);
        UiDraw.roundedBorder(graphics, getX() + 0.5F, boxY + 0.5F, boxSize - 1F, boxSize - 1F,
                Math.max(1F, UiTheme.RADIUS_SM - 0.5F), 1F, border);

        UiDraw.check(graphics, getX() + boxSize / 2F, boxY + boxSize / 2F, boxSize, mark,
                active ? UiTheme.ON_ACCENT : UiTheme.TEXT_DISABLED);

        Component label = getMessage();
        if(!label.getString().isEmpty()) {
            int labelLeft = getX() + boxSize + LABEL_GAP;
            int available = labelSpace();
            Component display = font.width(label) > available
                    ? Component.literal(font.plainSubstrByWidth(label.getString(), Math.max(1, available - 4)) + "…")
                    : label;
            int color = active ? UiTheme.mix(UiTheme.TEXT_SECONDARY, UiTheme.TEXT_STRONG, hover)
                    : UiTheme.TEXT_DISABLED;
            graphics.drawString(font, display, labelLeft, getY() + (height - font.lineHeight) / 2 + 1, color, false);
        }
        graphics.flush();
    }

    @Override
    public @Nullable Component focusedFullText() {
        if(!isHovered && !UiTruncatable.keyboardFocused(this)) return null;
        return UiText.width(getMessage()) > labelSpace() ? getMessage() : null;
    }

    private int boxSize() {
        return Math.min(BOX_SIZE, Math.max(6, height - 2));
    }

    private int labelSpace() {
        return Math.max(1, width - boxSize() - LABEL_GAP);
    }
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        playDownSound(Minecraft.getInstance().getSoundManager());
        checked = !checked;
        onToggle.accept(checked);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
