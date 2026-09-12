package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStorageOverview;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.save.StorageCleanupTarget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 存储清理必须在独立确认页再次确认，全部记录清理不会被普通按钮误触。 */
public final class ConfirmStorageCleanupScreen extends Screen {
    private static final int BUTTON_WIDTH = 96;
    private static final int BACKGROUND = 0xFF15181C;
    private final StorageOverviewScreen parent;
    private final StorageCleanupTarget target;

    private record Layout(int x, int top, int width, int height, List<ScreenLayout.Bounds> buttons) {}

    public ConfirmStorageCleanupScreen(StorageOverviewScreen parent, StorageCleanupTarget target) {
        super(DSKeyLang.ScreenStorageConfirmTitle.copy());
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        if(minecraft == null) return;
        Layout layout = layout();
        ScreenLayout.Bounds confirm = layout.buttons().getFirst();
        ScreenLayout.Bounds cancel = layout.buttons().getLast();
        addRenderableWidget(Button.builder(DSKeyLang.ScreenConfirm.copy(), button -> {
                    parent.cleanup(target);
                    minecraft.setScreen(parent);
                })
                .bounds(confirm.x(), confirm.y(), confirm.width(), confirm.height()).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(parent))
                .bounds(cancel.x(), cancel.y(), cancel.width(), cancel.height()).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout layout = layout();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        graphics.fill(layout.x(), layout.top(), Math.min(width, layout.x() + layout.width()),
                Math.min(height, layout.top() + layout.height()), 0xFF20262C);
        graphics.flush();
        graphics.drawCenteredString(font, truncate(title, layout.width() - 16), width / 2,
                Math.clamp(layout.top() + 16, 0, Math.max(0, height - 1)), 0xFFFFFFFF);
        Component description = description(target);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(description.getString(), Math.max(1, layout.width() - 16)),
                width / 2, Math.clamp(layout.top() + 34, 0, Math.max(0, height - 1)), 0xFFACB8C2);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        graphics.flush();
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
    }

    private Layout layout() {
        int panelWidth = Math.clamp(width - 8, 1, 264);
        int panelX = Math.max(0, (width - panelWidth) / 2);
        int buttonWidth = Math.clamp((panelWidth - 6) / 2, 1, BUTTON_WIDTH);
        boolean stacked = panelWidth < buttonWidth * 2 + 6;
        int rows = stacked ? 2 : 1;
        int panelHeight = Math.min(height, 108 + (stacked ? 24 : 0));
        int buttonHeight = Math.clamp((panelHeight - 64 - (rows - 1) * 4) / rows, 1, 20);
        int buttonTop = Math.clamp(panelHeight - (long) rows * buttonHeight - (rows - 1) * 4 - 2, 0, 54);
        int panelTop = Math.max(0, (height - panelHeight) / 2);
        int buttonsWidth = stacked ? buttonWidth : buttonWidth * 2 + 6;
        int buttonsX = panelX + Math.max(0, (panelWidth - buttonsWidth) / 2);
        ScreenLayout.Bounds confirm = new ScreenLayout.Bounds(buttonsX, panelTop + buttonTop,
                buttonWidth, buttonHeight);
        ScreenLayout.Bounds cancel = stacked
                ? new ScreenLayout.Bounds(buttonsX, confirm.y() + buttonHeight + 4, buttonWidth, buttonHeight)
                : new ScreenLayout.Bounds(buttonsX + buttonWidth + 6, confirm.y(), buttonWidth, buttonHeight);
        return new Layout(panelX, panelTop, panelWidth, panelHeight, List.of(confirm, cancel));
    }

    private Component truncate(Component text, int maxWidth) {
        if(maxWidth <= 0 || font.width(text) <= maxWidth) return text;
        int suffixWidth = font.width("...");
        return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(1, maxWidth - suffixWidth)) + "...");
    }

    private static Component description(StorageCleanupTarget target) {
        return switch (target) {
            case TEMPORARY_AND_BACKUPS -> DSKeyLang.ScreenStorageConfirmTemporary.copy();
            case EXPORTS -> DSKeyLang.ScreenStorageConfirmExports.copy();
            case ALL_RECORDS -> DSKeyLang.ScreenStorageConfirmAll.copy();
            case NONE -> ClientStorageOverview.cleanupLabel(target);
        };
    }
}
