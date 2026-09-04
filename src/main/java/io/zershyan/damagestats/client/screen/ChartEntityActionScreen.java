package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.filter.EntitySelector;
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
    private final StatsScreen parent;
    private final EntitySelector selector;
    private final Component entityName;
    private final boolean directSource;

    public ChartEntityActionScreen(StatsScreen parent, EntitySelector selector, Component entityName, boolean directSource) {
        super(DSKeyLang.ScreenChooseTypeAction.get(entityName));
        this.parent = parent;
        this.selector = selector;
        this.entityName = entityName;
        this.directSource = directSource;
    }

    @Override
    protected void init() {
        int x = width / 2 - PANEL_WIDTH / 2;
        int y = height / 2 - 42;
        if(directSource) {
            addRenderableWidget(Button.builder(DSKeyLang.ScreenFilter.copy(), button -> {
                        parent.toggleDirectFilter(selector, entityName);
                        minecraft.setScreen(parent);
                    })
                    .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
            y += 24;
        }
        if(selector instanceof EntitySelector.Instance) {
            if(directSource) {
                addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsDirectSource.copy(), button -> {
                            parent.selectAsDirectSource(selector, entityName);
                            minecraft.setScreen(parent);
                        })
                        .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
                y += 24;
            }
            if(parent.canChooseAnySource()) {
                addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsSource.copy(), button -> {
                            parent.selectAsSource(selector, entityName);
                            minecraft.setScreen(parent);
                        })
                        .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
                y += 24;
            }
            addRenderableWidget(Button.builder(DSKeyLang.ScreenSetAsTarget.copy(), button -> {
                        parent.selectAsTarget(selector, entityName);
                        minecraft.setScreen(parent);
                    })
                    .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
            y += 24;
        } else if(selector instanceof EntitySelector.Type type) {
            addRenderableWidget(Button.builder(DSKeyLang.ScreenInstances.copy(), button -> parent.openInstances(type, directSource))
                    .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
            y += 24;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(parent))
                .bounds(x, y, PANEL_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - PANEL_WIDTH / 2 - 8;
        int y = height / 2 - 60;
        graphics.fill(x, y, x + PANEL_WIDTH + 16, y + 140, 0xE020262C);
        graphics.drawCenteredString(font, title, width / 2, y + 10, 0xFFFFFFFF);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }
}
