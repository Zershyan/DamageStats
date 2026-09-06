package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.EntityChoiceRequestPacket;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import io.zershyan.damagestats.stats.view.EntityChoicePage;
import io.zershyan.damagestats.stats.view.EntityChoiceView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.BiConsumer;

/** 服务端实体候选的搜索与分页选择页，不枚举客户端本地或已加载实体。 */
public class FocusEntitySelectorScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 58;
    private static final int BOTTOM = 32;
    private static final int SIDE = 24;
    private static final int ROW_HOVER = 0x40FFFFFF;

    private final Screen parent;
    private final FocusSelectionSlot slot;
    private final @Nullable ResourceLocation typeFilter;
    private final BiConsumer<EntitySelector, Component> selectionConsumer;
    private final StatsFilter contextFilter;
    private int cursor;
    private int requestId;
    private int rowOffset;
    private @Nullable EntityChoiceView selected;
    private @Nullable EntityChoicePage adopted;
    private EditBox searchBox;
    private Button setButton;
    private Button instancesButton;
    private Button previousButton;
    private Button nextButton;

    public FocusEntitySelectorScreen(Screen parent, FocusSelectionSlot slot,
                                     BiConsumer<EntitySelector, Component> selectionConsumer) {
        this(parent, slot, null, StatsFilter.NONE, selectionConsumer);
    }

    public FocusEntitySelectorScreen(Screen parent, FocusSelectionSlot slot, StatsFilter contextFilter,
                                     BiConsumer<EntitySelector, Component> selectionConsumer) {
        this(parent, slot, null, contextFilter, selectionConsumer);
    }

    private FocusEntitySelectorScreen(Screen parent, FocusSelectionSlot slot, @Nullable ResourceLocation typeFilter,
                                      StatsFilter contextFilter, BiConsumer<EntitySelector, Component> selectionConsumer) {
        super(titleFor(slot));
        this.parent = parent;
        this.slot = slot;
        this.typeFilter = typeFilter;
        this.contextFilter = contextFilter;
        this.selectionConsumer = selectionConsumer;
    }

    public static FocusEntitySelectorScreen directSourceInstances(Screen parent, ResourceLocation typeFilter,
                                                                    StatsFilter contextFilter,
                                                                    BiConsumer<EntitySelector, Component> selectionConsumer) {
        return new FocusEntitySelectorScreen(parent, FocusSelectionSlot.DIRECT_SOURCE, typeFilter,
                contextFilter, selectionConsumer);
    }

    public static FocusEntitySelectorScreen instances(Screen parent, FocusSelectionSlot slot, ResourceLocation typeFilter,
                                                       StatsFilter contextFilter,
                                                       BiConsumer<EntitySelector, Component> selectionConsumer) {
        return new FocusEntitySelectorScreen(parent, slot, typeFilter, contextFilter, selectionConsumer);
    }

    @Override
    protected void init() {
        int searchWidth = Math.min(260, width - SIDE * 2 - 56);
        searchBox = addRenderableWidget(new EditBox(font, SIDE, 30, searchWidth, 18, DSKeyLang.ScreenSearch.copy()));
        searchBox.setHint(DSKeyLang.ScreenSearch.copy());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenSearch.copy(), button -> request(0))
                .bounds(SIDE + searchWidth + 4, 30, 52, 18).build());
        setButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenSelect.copy(), button -> select())
                .bounds(width - SIDE - 108, height - BOTTOM, 108, 20).build());
        instancesButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenInstances.copy(), button -> openInstances())
                .bounds(width - SIDE - 218, height - BOTTOM, 106, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> minecraft.setScreen(parent))
                .bounds(SIDE, height - BOTTOM, 80, 20).build());
        previousButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenPrevious.copy(), button -> request(cursor - EntityChoicePage.PAGE_SIZE))
                .bounds(SIDE + 84, height - BOTTOM, 72, 20).build());
        nextButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenNext.copy(), button -> request(cursor + EntityChoicePage.PAGE_SIZE))
                .bounds(SIDE + 160, height - BOTTOM, 72, 20).build());
        request(cursor);
        refreshActions();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        EntityChoicePage page = ClientStats.entityChoices();
        if(page != null && page != adopted && page.requestId() == requestId
                && page.slot() == slot && page.typeFilter().equals(Optional.ofNullable(typeFilter))) {
            adopted = page;
            selected = null;
            rowOffset = 0;
            refreshActions();
        }
        if(page != null && (page.requestId() != requestId || page.slot() != slot
                || !page.typeFilter().equals(Optional.ofNullable(typeFilter)))) page = null;
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        if(page == null || !page.allowed()) {
            graphics.drawCenteredString(font, DSKeyLang.NoData.copy(), width / 2, TOP, 0xFFAAAAAA);
        } else {
            renderRows(graphics, page, mouseX, mouseY);
        }
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }

    private void renderRows(GuiGraphics graphics, EntityChoicePage page, int mouseX, int mouseY) {
        int rows = Math.min(visibleRows(), page.entries().size() - rowOffset);
        for (int row = 0; row < rows; row++) {
            EntityChoiceView entry = page.entries().get(rowOffset + row);
            int y = TOP + row * ROW_HEIGHT;
            if(isInsideRow(mouseX, mouseY, y)) graphics.fill(SIDE, y, width - SIDE, y + ROW_HEIGHT - 1, ROW_HOVER);
            if(entry == selected) graphics.fill(SIDE, y, width - SIDE, y + ROW_HEIGHT - 1, 0x4066CCFF);
            int textWidth = Math.max(1, width - SIDE * 2 - 4);
            graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), textWidth),
                    SIDE + 2, y + 2, 0xFFFFFFFF);
            if(!entry.detail().getString().isEmpty()) {
                graphics.drawString(font, font.plainSubstrByWidth(entry.detail().getString(), textWidth),
                        SIDE + 2, y + 12, 0xFFAAAAAA);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(button == 0 && adopted != null) {
            for (int row = 0; row < Math.min(visibleRows(), adopted.entries().size() - rowOffset); row++) {
                int y = TOP + row * ROW_HEIGHT;
                if(!isInsideRow(mouseX, mouseY, y)) continue;
                selected = adopted.entries().get(rowOffset + row);
                refreshActions();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(adopted == null || mouseY < TOP || mouseY > height - BOTTOM - 4) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int max = Math.max(0, adopted.entries().size() - visibleRows());
        rowOffset = Math.clamp(rowOffset - (int) Math.signum(scrollY), 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == 257 || keyCode == 335) {
            request(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void request(int newCursor) {
        cursor = Math.max(0, newCursor);
        adopted = null;
        selected = null;
        rowOffset = 0;
        requestId = ClientStats.nextEntityChoiceRequestId();
        PacketDistributor.sendToServer(new EntityChoiceRequestPacket(slot, Optional.ofNullable(typeFilter), searchBox == null
                ? "" : searchBox.getValue(), cursor, requestId, contextFilter));
    }

    private void select() {
        if(selected == null) return;
        selectionConsumer.accept(selected.selector(), selected.name());
        minecraft.setScreen(parent);
    }

    private void openInstances() {
        if(!(selected != null && selected.selector() instanceof EntitySelector.Type type)) return;
        minecraft.setScreen(new FocusEntitySelectorScreen(parent, slot, type.typeId(), contextFilter, selectionConsumer));
    }

    private void refreshActions() {
        if(setButton != null) setButton.active = selected != null;
        if(instancesButton != null) instancesButton.active = selected != null && selected.selector() instanceof EntitySelector.Type;
        if(previousButton != null) previousButton.active = cursor > 0;
        if(nextButton != null) nextButton.active = adopted != null && adopted.hasNext();
    }

    private int visibleRows() {
        return Math.max(1, (height - BOTTOM - TOP - 6) / ROW_HEIGHT);
    }

    private boolean isInsideRow(double mouseX, double mouseY, int y) {
        return mouseX >= SIDE && mouseX <= width - SIDE && mouseY >= y && mouseY < y + ROW_HEIGHT - 1;
    }

    private static Component titleFor(FocusSelectionSlot slot) {
        return switch (slot) {
            case SOURCE -> DSKeyLang.FilterSource.get(Component.empty());
            case TARGET -> DSKeyLang.FilterTarget.get(Component.empty());
            case DIRECT_SOURCE -> DSKeyLang.FilterDirect.get(Component.empty());
        };
    }
}
