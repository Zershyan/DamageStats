package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 窄屏页尾收纳低频操作，避免和翻页、关闭按钮重叠。 */
public final class StatsActionsScreen extends Screen {
    private static final int PANEL_WIDTH = 180;
    private static final int BACKGROUND = 0xFF15181C;
    private final StatsScreen parent;
    private List<Component> tooltip = List.of();

    private record Layout(int x, int top, int width, int height, int titleHeight,
                          List<ScreenLayout.Bounds> buttons) {}

    public StatsActionsScreen(StatsScreen parent) {
        super(DSKeyLang.ScreenActions.copy());
        this.parent = parent;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int index = 0;
        ScreenLayout.Bounds details = layout.buttons().get(index++);
        addRenderableWidget(Button.builder(DSKeyLang.ScreenDetails.copy(), button -> parent.openDetails())
                .bounds(details.x(), details.y(), details.width(), details.height()).build());
        ScreenLayout.Bounds overlay = layout.buttons().get(index++);
        addRenderableWidget(Button.builder(DSKeyLang.ScreenEditOverlay.copy(), button -> parent.openOverlay())
                .bounds(overlay.x(), overlay.y(), overlay.width(), overlay.height()).build());
        ScreenLayout.Bounds history = layout.buttons().get(index++);
        addRenderableWidget(Button.builder(DSKeyLang.ScreenHistory.copy(), button -> parent.openHistory())
                .bounds(history.x(), history.y(), history.width(), history.height()).build());
        ScreenLayout.Bounds export = layout.buttons().get(index++);
        Button exportButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenExport.copy(), button -> parent.requestExport())
                .bounds(export.x(), export.y(), export.width(), export.height()).build());
        exportButton.active = !ClientExportManager.isBusy();
        ScreenLayout.Bounds reset = layout.buttons().get(index++);
        addRenderableWidget(Button.builder(DSKeyLang.ScreenReset.copy(), button -> parent.openResetConfirmation())
                .bounds(reset.x(), reset.y(), reset.width(), reset.height()).build());
        if(parent.canResetAll()) {
            ScreenLayout.Bounds resetAll = layout.buttons().get(index++);
            addRenderableWidget(Button.builder(DSKeyLang.ScreenResetAll.copy(), button -> parent.openGlobalResetConfirmation())
                    .bounds(resetAll.x(), resetAll.y(), resetAll.width(), resetAll.height()).build());
        }
        ScreenLayout.Bounds cancel = layout.buttons().get(index);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(parent))
                .bounds(cancel.x(), cancel.y(), cancel.width(), cancel.height()).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout layout = layout();
        int x = Math.max(0, layout.x() - 8);
        int y = layout.top();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        graphics.fill(x, y, Math.min(width, x + layout.width() + 16),
                Math.min(height, y + layout.height()), 0xFF20262C);
        graphics.flush();
        Component displayTitle = truncate(title, Math.max(1, layout.width() - 12));
        graphics.drawCenteredString(font, displayTitle, width / 2,
                Math.clamp(y + 10, 0, Math.max(0, height - 1)), 0xFFFFFFFF);
        tooltip = font.width(title) > layout.width() - 12
                && mouseX >= x && mouseX <= x + layout.width() + 16
                && mouseY >= y && mouseY < y + layout.titleHeight()
                ? List.of(title) : List.of();
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        if(!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        graphics.flush();
        graphics.pose().popPose();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
    }

    private int buttonCount() {
        return (parent.canResetAll() ? 6 : 5) + 1;
    }

    private Layout layout() {
        int count = buttonCount();
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, width - 8));
        int titleHeight = Math.min(28, Math.max(1, height / 5));
        int padding = Math.min(8, Math.max(0, height / 12));
        int available = Math.max(1, height - titleHeight - padding * 2);
        int gap = count <= 1 ? 0 : Math.min(4, Math.max(0, (available - count) / (count - 1)));
        int buttonHeight = Math.min(20, Math.max(1, (available - gap * (count - 1)) / count));
        int panelHeight = titleHeight + padding * 2 + count * buttonHeight + gap * (count - 1);
        int top = Math.max(0, (height - panelHeight) / 2);
        int panelX = Math.max(0, (width - panelWidth) / 2);
        int buttonTop = top + padding + titleHeight;
        List<ScreenLayout.Bounds> buttons = ScreenLayout.flow(width, panelX, buttonTop, buttonHeight, gap,
                java.util.stream.IntStream.range(0, count).map(ignored -> panelWidth).toArray());
        return new Layout(panelX, top, panelWidth, panelHeight,
                titleHeight, buttons);
    }

    private Component truncate(Component text, int maxWidth) {
        if(maxWidth <= 0 || font.width(text) <= maxWidth) return text;
        int suffixWidth = font.width("...");
        return Component.literal(font.plainSubstrByWidth(text.getString(), Math.max(1, maxWidth - suffixWidth)) + "...");
    }
}
