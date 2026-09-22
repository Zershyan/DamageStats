package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 确认类弹窗的公共外壳：警示图标 + 标题 + 说明 + 取消/确认。子类只负责确认动作与文案。
 * 背景沿用主界面的渐变再压暗，切页时不会出现风格断裂。
 */
abstract class ConfirmDialogScreen extends UiScreen {
    private static final int PANEL_MAX_WIDTH = 248;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ICON_BLOCK = 26;
    private static final int LINE_HEIGHT = 10;

    private final Screen returnScreen;
    private final Component description;
    private final Component confirmLabel;

    private List<FormattedCharSequence> descriptionLines = List.of();
    private UiLayout.Rect panel = new UiLayout.Rect(0, 0, 0, 0);

    protected ConfirmDialogScreen(Screen returnScreen, Component title, Component description,
                                  Component confirmLabel) {
        super(title);
        this.returnScreen = returnScreen;
        this.description = description;
        this.confirmLabel = confirmLabel;
    }

    /** 用户点下确认后执行的动作。 */
    protected abstract void onConfirm();

    @Override
    protected void init() {
        if(minecraft == null) return;
        panel = layout();
        UiLayout.Rect buttons = panel.dropBottom(12, 12).takeBottom(BUTTON_HEIGHT);
        // 取消放在左侧、确认放右侧，危险操作不放在顺手位上
        List<UiLayout.Rect> row = UiLayout.row(buttons, 6, 1, 1);
        addRenderableWidget(UiButton.builder(CommonComponents.GUI_CANCEL, button ->
                        minecraft.setScreen(returnScreen))
                .bounds(row.get(0)).variant(UiButton.Variant.GHOST).build());
        addRenderableWidget(UiButton.builder(confirmLabel, button -> {
                    onConfirm();
                    minecraft.setScreen(returnScreen);
                })
                .bounds(row.get(1)).variant(UiButton.Variant.DANGER).build());
    }

    @Override
    protected void renderContent(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderModalScrim(graphics);
        renderCard(graphics, panel, true);
        renderDialogBody(graphics);
    }

    private void renderDialogBody(GuiGraphics graphics) {
        int centerX = panel.centerX();
        int iconY = panel.y() + 16;
        int accent = UiTheme.DANGER;
        UiDraw.circle(graphics, centerX, iconY + ICON_BLOCK / 2F, ICON_BLOCK / 2F,
                UiTheme.alpha(accent, 0.16F));
        UiDraw.roundedBorder(graphics, centerX - ICON_BLOCK / 2F, iconY, ICON_BLOCK, ICON_BLOCK,
                ICON_BLOCK / 2F, 1F, UiTheme.alpha(accent, 0.5F));
        // 惊叹号：一竖加一点，比单纯的色块更直接地传达「不可逆」
        UiDraw.line(graphics, centerX, iconY + 6F, centerX, iconY + 14F, 2F, accent);
        UiDraw.circle(graphics, centerX, iconY + 18F, 1.2F, accent);

        int textTop = iconY + ICON_BLOCK + 10;
        UiText.boldCentered(graphics, UiText.truncateBold(title, Math.max(1, panel.width() - 24)), centerX,
                textTop, UiTheme.TEXT_STRONG);
        int lineTop = textTop + font.lineHeight + 5;
        for(int index = 0; index < descriptionLines.size(); index++) {
            graphics.drawCenteredString(font, descriptionLines.get(index), centerX,
                    lineTop + index * LINE_HEIGHT, UiTheme.TEXT_SECONDARY);
        }
        graphics.flush();
    }

    private UiLayout.Rect layout() {
        int panelWidth = Math.clamp(width - 16, 1, PANEL_MAX_WIDTH);
        descriptionLines = wrapLines(description, Math.max(1, panelWidth - 24));
        int panelHeight = 16 + ICON_BLOCK + 10 + font.lineHeight + 5
                + descriptionLines.size() * LINE_HEIGHT + 14 + BUTTON_HEIGHT + 12;
        return modalPanel(width, height, panelWidth, panelHeight);
    }
}
