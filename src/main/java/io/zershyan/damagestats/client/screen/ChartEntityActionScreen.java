package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.FilterKey;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 图表实体行的操作菜单，类型必须先进入实例列表后才能成为具体直接来源。 */
public final class ChartEntityActionScreen extends Screen {
    private static final int PANEL_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private final StatsScreen parent;
    private final FilterKey key;
    private final EntitySelector selector;
    private final Component entityName;
    private List<Component> tooltip = List.of();

    private record Layout(int x, int top, int width, int height, int titleHeight,
                          List<ScreenLayout.Bounds> buttons) {}

    public ChartEntityActionScreen(StatsScreen parent, FilterKey key, Component entityName) {
        super(DSKeyLang.ScreenChooseTypeAction.get(entityName));
        this.parent = parent;
        this.key = key;
        this.selector = selector(key);
        this.entityName = entityName;
    }

    @Override
    protected void init() {
        Layout layout = layout();
        int buttonIndex = 0;
        if(key instanceof FilterKey.Direct) {
            ScreenLayout.Bounds bounds = layout.buttons().get(buttonIndex++);
            addRenderableWidget(Button.builder(DSKeyLang.ScreenFilter.copy(), button -> {
                        parent.toggleDirectFilter(selector, entityName);
                        minecraft.setScreen(parent);
                    })
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
        }
        if(selector instanceof EntitySelector.Instance) {
            if(key instanceof FilterKey.Direct) {
                addSetAsSourceButton(layout.buttons().get(buttonIndex++), true);
            } else if(parent.canChooseAnySource() && (key instanceof FilterKey.Source || key instanceof FilterKey.Target)) {
                addSetAsSourceButton(layout.buttons().get(buttonIndex++), false);
            }
            ScreenLayout.Bounds bounds = layout.buttons().get(buttonIndex++);
            addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsTarget.copy(), button -> {
                        parent.selectAsTarget(selector, entityName);
                        minecraft.setScreen(parent);
                    })
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
        } else if(selector instanceof EntitySelector.Type type) {
            if(key instanceof FilterKey.Source) {
                if(parent.canChooseAnySource()) {
                    addSetAsSourceButton(layout.buttons().get(buttonIndex++), false);
                }
                addSetAsTargetButton(layout.buttons().get(buttonIndex++));
            } else if(key instanceof FilterKey.Target) {
                addSetAsTargetButton(layout.buttons().get(buttonIndex++));
                if(parent.canChooseAnySource()) {
                    addSetAsSourceButton(layout.buttons().get(buttonIndex++), false);
                }
            }
            ScreenLayout.Bounds bounds = layout.buttons().get(buttonIndex++);
            addRenderableWidget(Button.builder(DSKeyLang.ScreenInstances.copy(), button ->
                            parent.openInstances(type, key))
                    .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
        }
        ScreenLayout.Bounds cancel = layout.buttons().get(buttonIndex);
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(parent))
                .bounds(cancel.x(), cancel.y(), cancel.width(), cancel.height()).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        Layout layout = layout();
        int x = Math.max(0, layout.x() - 8);
        int y = layout.top();
        graphics.fill(x, y, Math.min(width, x + layout.width() + 16),
                Math.min(height, y + layout.height()), 0xE020262C);
        Component displayTitle = truncate(title, Math.max(1, layout.width() - 12));
        graphics.drawCenteredString(font, displayTitle, width / 2,
                Math.clamp(y + 10, 0, Math.max(0, height - 1)), 0xFFFFFFFF);
        tooltip = font.width(title) > layout.width() - 12
                && mouseX >= x && mouseX <= x + layout.width() + 16
                && mouseY >= y && mouseY < y + layout.titleHeight()
                ? List.of(title) : List.of();
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        if(!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    private void addSetAsSourceButton(ScreenLayout.Bounds bounds, boolean directSource) {
        addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsSource.copy(), button -> {
                    if(directSource) parent.selectAsDirectSource(selector, entityName);
                    else parent.selectAsSource(selector, entityName);
                    minecraft.setScreen(parent);
                })
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
    }

    private void addSetAsTargetButton(ScreenLayout.Bounds bounds) {
        addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsTarget.copy(), button -> {
                    parent.selectAsTarget(selector, entityName);
                    minecraft.setScreen(parent);
                })
                .bounds(bounds.x(), bounds.y(), bounds.width(), bounds.height()).build());
    }

    private int buttonCount() {
        int count = key instanceof FilterKey.Direct ? 1 : 0;
        if(selector instanceof EntitySelector.Instance) {
            if(key instanceof FilterKey.Direct
                    || parent.canChooseAnySource() && (key instanceof FilterKey.Source || key instanceof FilterKey.Target)) count++;
            count++;
        } else if(selector instanceof EntitySelector.Type) {
            if(key instanceof FilterKey.Source) {
                if(parent.canChooseAnySource()) count++;
                count++;
            } else if(key instanceof FilterKey.Target) {
                count++;
                if(parent.canChooseAnySource()) count++;
            }
            count++;
        }
        return count + 1;
    }

    private Layout layout() {
        int count = buttonCount();
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(1, width - 8));
        int titleHeight = Math.min(28, Math.max(1, height / 5));
        int padding = Math.min(8, Math.max(0, height / 12));
        int available = Math.max(1, height - titleHeight - padding * 2);
        int gap = count <= 1 ? 0 : Math.min(4, Math.max(0, (available - count) / (count - 1)));
        int buttonHeight = Math.min(BUTTON_HEIGHT, Math.max(1, (available - gap * (count - 1)) / count));
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

    private static EntitySelector selector(FilterKey key) {
        return switch (key) {
            case FilterKey.Source(EntitySelector selector) -> selector;
            case FilterKey.Target(EntitySelector selector) -> selector;
            case FilterKey.Direct(EntitySelector selector) -> selector;
            case FilterKey.Type ignored -> throw new IllegalArgumentException("实体操作菜单不能处理伤害类型");
        };
    }
}
