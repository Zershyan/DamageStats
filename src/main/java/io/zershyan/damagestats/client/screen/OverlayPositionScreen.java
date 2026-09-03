package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.overlay.StatsOverlay;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * 浮层位置编辑。拖拽时坐标一律换算成屏幕比例存回配置，
 * 并且夹住不让浮层被拖出可视区域——比事后靠「重置位置」补救更省事。
 */
public class OverlayPositionScreen extends Screen {
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 5;
    private static final int BUTTON_BOTTOM_MARGIN = 30;
    private static final int TITLE_Y = 20;
    private static final int DIM_BACKGROUND = 0x40000000;
    private static final int PREVIEW_BORDER = 0xFFFFD700;

    private double ratioX;
    private double ratioY;
    private boolean dragging;
    private int grabOffsetX;
    private int grabOffsetY;

    public OverlayPositionScreen() {
        super(DSKeyLang.EditPositionTitle.copy());
        this.ratioX = DSClientConfig.OverlayX.get();
        this.ratioY = DSClientConfig.OverlayY.get();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int buttonY = height - BUTTON_BOTTOM_MARGIN;
        addRenderableWidget(Button.builder(DSKeyLang.EditPositionDone.copy(), button -> onClose())
                .bounds(centerX - BUTTON_WIDTH - BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(DSKeyLang.EditPositionReset.copy(), button -> resetPosition())
                .bounds(centerX + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    /** 只压一层很淡的暗色，让玩家还能看清浮层和游戏画面的相对位置 */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DIM_BACKGROUND);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, TITLE_Y, 0xFFFFFFFF);
        int x = originX();
        int y = originY();
        StatsOverlay.renderBox(graphics, font, StatsOverlay.previewSummary(), x, y);
        graphics.renderOutline(x, y, StatsOverlay.WIDTH, StatsOverlay.height(), PREVIEW_BORDER);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(button == 0 && isInsidePreview(mouseX, mouseY)) {
            dragging = true;
            grabOffsetX = (int) (mouseX - originX());
            grabOffsetY = (int) (mouseY - originY());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(!dragging) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        ratioX = clampRatio((mouseX - grabOffsetX) / width, StatsOverlay.WIDTH, width);
        ratioY = clampRatio((mouseY - grabOffsetY) / height, StatsOverlay.height(), height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        DSClientConfig.OverlayX.set(ratioX);
        DSClientConfig.OverlayY.set(ratioY);
        DSClientConfig.OverlayX.save();
        super.onClose();
    }

    private void resetPosition() {
        ratioX = DSClientConfig.DefaultOverlayX;
        ratioY = DSClientConfig.DefaultOverlayY;
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        int x = originX();
        int y = originY();
        return mouseX >= x && mouseX <= x + StatsOverlay.WIDTH
                && mouseY >= y && mouseY <= y + StatsOverlay.height();
    }

    private int originX() {
        return (int) (width * ratioX);
    }

    private int originY() {
        return (int) (height * ratioY);
    }

    private static double clampRatio(double ratio, int elementSize, int screenSize) {
        if(screenSize <= elementSize) return 0;
        return Mth.clamp(ratio, 0, 1.0 - (double) elementSize / screenSize);
    }
}
