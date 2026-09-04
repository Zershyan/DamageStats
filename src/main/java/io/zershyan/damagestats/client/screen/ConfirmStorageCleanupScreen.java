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

/** 存储清理必须在独立确认页再次确认，全部记录清理不会被普通按钮误触。 */
public final class ConfirmStorageCleanupScreen extends Screen {
    private static final int BUTTON_WIDTH = 96;
    private final StorageOverviewScreen parent;
    private final StorageCleanupTarget target;

    public ConfirmStorageCleanupScreen(StorageOverviewScreen parent, StorageCleanupTarget target) {
        super(DSKeyLang.ScreenStorageConfirmTitle.copy());
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void init() {
        int y = height / 2 + 10;
        addRenderableWidget(Button.builder(DSKeyLang.ScreenConfirm.copy(), button -> {
                    parent.cleanup(target);
                    minecraft.setScreen(parent);
                })
                .bounds(width / 2 - BUTTON_WIDTH - 3, y, BUTTON_WIDTH, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(parent))
                .bounds(width / 2 + 3, y, BUTTON_WIDTH, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 132;
        int y = height / 2 - 44;
        graphics.fill(x, y, x + 264, y + 108, 0xE020262C);
        graphics.drawCenteredString(font, title, width / 2, y + 16, 0xFFFFFFFF);
        Component description = description(target);
        graphics.drawCenteredString(font, font.plainSubstrByWidth(description.getString(), 240), width / 2, y + 34,
                0xFFACB8C2);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
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
