package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/** 个人统计清空必须经过单独确认，防止在固定页尾误触。 */
public final class ConfirmResetScreen extends Screen {
    private static final int BUTTON_WIDTH = 96;
    private final StatsScreen parent;
    private final boolean global;

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
        int y = height / 2 + 10;
        addRenderableWidget(Button.builder(DSKeyLang.ScreenConfirm.copy(), button -> {
                    parent.confirmReset(global);
                    minecraft.setScreen(parent);
                })
                .bounds(width / 2 - BUTTON_WIDTH - 3, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(parent))
                .bounds(width / 2 + 3, y, BUTTON_WIDTH, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 124;
        int y = height / 2 - 44;
        graphics.fill(x, y, x + 248, y + 108, 0xE020262C);
        graphics.drawCenteredString(font, title, width / 2, y + 16, 0xFFFFFFFF);
        Component description = global ? DSKeyLang.ScreenResetAllDescription.copy() : DSKeyLang.ScreenResetMineDescription.copy();
        graphics.drawCenteredString(font, font.plainSubstrByWidth(description.getString(), 224), width / 2, y + 34, 0xFFACB8C2);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }
}
