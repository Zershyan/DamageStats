package io.zershyan.damagestats.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** 勾选框：整行可点，选中时方块填充强调色并淡入一个对勾。 */
public class UiCheckbox extends AbstractWidget {
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

        int boxSize = Math.min(BOX_SIZE, Math.max(6, height - 2));
        float boxY = getY() + (height - boxSize) / 2F;
        int boxTop = UiTheme.mix(UiTheme.SURFACE, UiTheme.SURFACE_HOVER, hover * 0.7F);
        int boxBottom = UiTheme.mix(UiTheme.BACKDROP, UiTheme.SURFACE, hover * 0.7F);
        int fillTop = UiTheme.mix(boxTop, UiTheme.ACCENT_BRIGHT, mark);
        int fillBottom = UiTheme.mix(boxBottom, UiTheme.ACCENT, mark);
        UiDraw.roundedRect(graphics, getX(), boxY, boxSize, boxSize, UiTheme.RADIUS_SM,
                active ? fillTop : UiTheme.alpha(fillTop, 0.5F), active ? fillBottom : UiTheme.alpha(fillBottom, 0.5F));
        UiDraw.roundedBorder(graphics, getX() + 0.5F, boxY + 0.5F, boxSize - 1F, boxSize - 1F,
                Math.max(1F, UiTheme.RADIUS_SM - 0.5F), 1F,
                UiTheme.mix(UiTheme.BORDER_STRONG, UiTheme.ACCENT_BRIGHT, mark));

        if(mark > 0.02F) {
            int markColor = UiTheme.alpha(UiTheme.ON_ACCENT, active ? mark : mark * 0.5F);
            float left = getX() + boxSize * 0.26F;
            float middleX = getX() + boxSize * 0.44F;
            float right = getX() + boxSize * 0.76F;
            float middleY = boxY + boxSize * 0.66F;
            float topY = boxY + boxSize * 0.34F;
            // 对勾随进度从短变长，得到「画上去」的手感
            UiDraw.line(graphics, left, middleY, middleX, topY + (middleY - topY) * (1F - mark) * 2F,
                    1.6F, markColor);
            UiDraw.line(graphics, middleX, topY + (middleY - topY) * (1F - mark) * 2F, right,
                    middleY - (middleY - topY) * mark, 1.6F, markColor);
        }

        Component label = getMessage();
        if(!label.getString().isEmpty()) {
            int labelLeft = (int) (getX() + boxSize + LABEL_GAP);
            int available = Math.max(1, getRight() - labelLeft);
            Component display = font.width(label) > available
                    ? Component.literal(font.plainSubstrByWidth(label.getString(), Math.max(1, available - 4)) + "…")
                    : label;
            int color = active ? UiTheme.mix(UiTheme.TEXT_SECONDARY, UiTheme.TEXT_STRONG, hover)
                    : UiTheme.TEXT_DISABLED;
            graphics.drawString(font, display, labelLeft, getY() + (height - font.lineHeight) / 2 + 1, color, false);
            UiDraw.roundedBorder(graphics, getX() + 0.5F, getY() + 0.5F, width - 1F, height - 1F,
                    UiTheme.RADIUS_SM, 1F, UiTheme.alpha(UiTheme.ACCENT, 0.22F * hover));
        }
        graphics.flush();
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
