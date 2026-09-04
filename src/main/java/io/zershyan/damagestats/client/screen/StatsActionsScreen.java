package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import org.jetbrains.annotations.NotNull;

/** 窄屏页尾收纳低频操作，避免和翻页、关闭按钮重叠。 */
public final class StatsActionsScreen extends Screen {
    private static final int PANEL_WIDTH = 180;
    private final StatsScreen parent;

    public StatsActionsScreen(StatsScreen parent) {
        super(DSKeyLang.ScreenActions.copy());
        this.parent = parent;
    }

    @Override
    protected void init() {
        int actionCount = parent.canResetAll() ? 6 : 5;
        int x = width / 2 - PANEL_WIDTH / 2;
        int y = height / 2 - (actionCount * 24 + 20) / 2;
        addRenderableWidget(Button.builder(DSKeyLang.ScreenDetails.copy(), button -> parent.openDetails())
                .bounds(x, y, PANEL_WIDTH, 20).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenEditOverlay.copy(), button -> parent.openOverlay())
                .bounds(x, y + 24, PANEL_WIDTH, 20).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenHistory.copy(), button -> parent.openHistory())
                .bounds(x, y + 48, PANEL_WIDTH, 20).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenExport.copy(), button -> parent.requestExport())
                .bounds(x, y + 72, PANEL_WIDTH, 20).build()).active = !ClientExportManager.isBusy();
        addRenderableWidget(Button.builder(DSKeyLang.ScreenReset.copy(), button -> parent.openResetConfirmation())
                .bounds(x, y + 96, PANEL_WIDTH, 20).build());
        if(parent.canResetAll()) {
            addRenderableWidget(Button.builder(DSKeyLang.ScreenResetAll.copy(), button -> parent.openGlobalResetConfirmation())
                    .bounds(x, y + 120, PANEL_WIDTH, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(parent))
                .bounds(x, y + actionCount * 24, PANEL_WIDTH, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - PANEL_WIDTH / 2 - 8;
        int actionCount = parent.canResetAll() ? 6 : 5;
        int y = height / 2 - (actionCount * 24 + 36) / 2;
        graphics.fill(x, y, x + PANEL_WIDTH + 16, y + actionCount * 24 + 36, 0xE020262C);
        graphics.drawCenteredString(font, title, width / 2, y + 10, 0xFFFFFFFF);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }
}
