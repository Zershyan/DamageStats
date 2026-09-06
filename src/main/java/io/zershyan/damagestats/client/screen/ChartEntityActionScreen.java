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

/** 图表实体行的操作菜单，类型必须先进入实例列表后才能成为具体直接来源。 */
public final class ChartEntityActionScreen extends Screen {
    private static final int PANEL_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_STEP = 24;
    private final StatsScreen parent;
    private final FilterKey key;
    private final EntitySelector selector;
    private final Component entityName;

    public ChartEntityActionScreen(StatsScreen parent, FilterKey key, Component entityName) {
        super(DSKeyLang.ScreenChooseTypeAction.get(entityName));
        this.parent = parent;
        this.key = key;
        this.selector = selector(key);
        this.entityName = entityName;
    }

    @Override
    protected void init() {
        int x = width / 2 - PANEL_WIDTH / 2;
        int y = panelTop() + 28;
        if(key instanceof FilterKey.Direct) {
            addRenderableWidget(Button.builder(DSKeyLang.ScreenFilter.copy(), button -> {
                        parent.toggleDirectFilter(selector, entityName);
                        minecraft.setScreen(parent);
                    })
                    .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
            y += BUTTON_STEP;
        }
        if(selector instanceof EntitySelector.Instance) {
            if(key instanceof FilterKey.Direct) {
                addSetAsSourceButton(x, y, true);
                y += BUTTON_STEP;
            } else if(parent.canChooseAnySource() && (key instanceof FilterKey.Source || key instanceof FilterKey.Target)) {
                addSetAsSourceButton(x, y, false);
                y += BUTTON_STEP;
            }
            addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsTarget.copy(), button -> {
                        parent.selectAsTarget(selector, entityName);
                        minecraft.setScreen(parent);
                    })
                    .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
            y += BUTTON_STEP;
        } else if(selector instanceof EntitySelector.Type type) {
            if(key instanceof FilterKey.Source) {
                if(parent.canChooseAnySource()) {
                    addSetAsSourceButton(x, y, false);
                    y += BUTTON_STEP;
                }
                addSetAsTargetButton(x, y);
                y += BUTTON_STEP;
            } else if(key instanceof FilterKey.Target) {
                addSetAsTargetButton(x, y);
                y += BUTTON_STEP;
                if(parent.canChooseAnySource()) {
                    addSetAsSourceButton(x, y, false);
                    y += BUTTON_STEP;
                }
            }
            addRenderableWidget(Button.builder(DSKeyLang.ScreenInstances.copy(), button ->
                            parent.openInstances(type, key))
                    .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
            y += BUTTON_STEP;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(parent))
                .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - PANEL_WIDTH / 2 - 8;
        int y = panelTop();
        graphics.fill(x, y, x + PANEL_WIDTH + 16, y + panelHeight(), 0xE020262C);
        graphics.drawCenteredString(font, title, width / 2, y + 10, 0xFFFFFFFF);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }

    private void addSetAsSourceButton(int x, int y, boolean directSource) {
        addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsSource.copy(), button -> {
                    if(directSource) parent.selectAsDirectSource(selector, entityName);
                    else parent.selectAsSource(selector, entityName);
                    minecraft.setScreen(parent);
                })
                .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
    }

    private void addSetAsTargetButton(int x, int y) {
        addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsTarget.copy(), button -> {
                    parent.selectAsTarget(selector, entityName);
                    minecraft.setScreen(parent);
                })
                .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
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

    private int panelHeight() {
        return 40 + buttonCount() * BUTTON_HEIGHT + (buttonCount() - 1) * 4;
    }

    private int panelTop() {
        return Math.max(4, (height - panelHeight()) / 2);
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
