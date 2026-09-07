package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 个人统计清空必须经过单独确认，防止在固定页尾误触。 */
public final class ConfirmResetScreen extends Screen {
    private static final int BUTTON_WIDTH = 96;
    private final StatsScreen parent;
    private final boolean global;

    private record Layout(int x, int top, int width, int height, List<ScreenLayout.Bounds> buttons) {}

    public ConfirmResetScreen(StatsScreen parent) {
        this(parent, false);
    }

    public ConfirmResetScreen(StatsScreen parent, boolean global) {
        super(global ? DSKeyLang.ScreenConfirmResetAll.copy() : DSKeyLang.ScreenConfirmReset.copy());
        this.parent = parent;
        this.global = global;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        ScreenLayout.Bounds confirm = layout.buttons().getFirst();
        ScreenLayout.Bounds cancel = layout.buttons().getLast();
        addRenderableWidget(Button.builder(DSKeyLang.ScreenConfirm.copy(), button -> {
                    parent.confirmReset(global);
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
        graphics.fill(layout.x(), layout.top(), Math.min(width, layout.x() + layout.width()),
                Math.min(height, layout.top() + layout.height()), 0xE020262C);
        graphics.drawCenteredString(font, truncate(title, layout.width() - 16), width / 2,
                Math.clamp(layout.top() + 16, 0, Math.max(0, height - 1)), 0xFFFFFFFF);
        Component description = global ? DSKeyLang.ScreenResetAllDescription.copy() : DSKeyLang.ScreenResetMineDescription.copy();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(description.getString(), Math.max(1, layout.width() - 16)),
                width / 2, Math.clamp(layout.top() + 34, 0, Math.max(0, height - 1)), 0xFFACB8C2);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }

    private Layout layout() {
        int panelWidth = Math.min(248, Math.max(1, width - 8));
        int panelX = Math.max(0, (width - panelWidth) / 2);
        int buttonWidth = Math.min(BUTTON_WIDTH, Math.max(1, (panelWidth - 6) / 2));
        boolean stacked = panelWidth < buttonWidth * 2 + 6;
        int rows = stacked ? 2 : 1;
        int panelHeight = Math.min(height, 108 + (stacked ? 24 : 0));
        int buttonHeight = Math.min(20, Math.max(1,
                (panelHeight - 64 - (rows - 1) * 4) / rows));
        int buttonTop = Math.max(0, Math.min(54,
                panelHeight - rows * buttonHeight - (rows - 1) * 4 - 2));
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
}
