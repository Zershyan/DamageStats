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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

/** 服务端实体候选的搜索与分页选择页，不枚举客户端本地或已加载实体。 */
public class FocusEntitySelectorScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int TOP = 58;
    private static final int SIDE = 24;
    private static final int ROW_HOVER = 0x40FFFFFF;
    private static final int RECENT_MENU_ROWS = 8;
    private static final int RECENT_MENU_WIDTH = 220;

    private final Screen parent;
    private final FocusSelectionSlot slot;
    private final @Nullable ResourceLocation typeFilter;
    private final BiConsumer<EntitySelector, Component> selectionConsumer;
    private final StatsFilter contextFilter;
    private String cursor = "";
    private final List<String> cursorHistory = new ArrayList<>();
    private int requestId;
    private int rowOffset;
    private @Nullable EntityChoiceView selected;
    private @Nullable EntityChoicePage adopted;
    private @Nullable EditBox searchBox;
    private @Nullable Button recentButton;
    private boolean recentMenuOpen;
    private List<EntityChoiceView> recentChoices = List.of();
    private int recentRequestId = -1;
    private Button setButton;
    private Button instancesButton;
    private Button previousButton;
    private Button nextButton;
    private List<Component> rowTooltip = List.of();

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
        ScreenLayout.Flow footer = footerFlow();
        int left = contentLeft();
        int available = contentWidth();
        int searchTop = searchTop();
        if(searchTop >= 0) {
            int controlsLeft = left;
            if(typeFilter == null && available >= 2) {
                int controlGap = Math.min(4, Math.max(0, available - 2));
                int reservedSearchWidth = Math.min(64, Math.max(1, (available - controlGap) / 2));
                int preferredRecentWidth = Math.min(120, Math.max(64, available / 3));
                int recentWidth = Math.min(preferredRecentWidth,
                        Math.max(1, available - controlGap - reservedSearchWidth));
                recentButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenRecent.copy(), button ->
                                toggleRecentMenu())
                        .bounds(controlsLeft, searchTop, recentWidth, 18).build());
                controlsLeft += recentWidth + controlGap;
            }
            int remaining = Math.max(1, contentRight() - controlsLeft);
            boolean showSearchButton = remaining >= 92;
            int searchButtonWidth = showSearchButton ? Math.min(52, Math.max(40, remaining / 3)) : 0;
            int searchWidth = Math.max(1, remaining - (showSearchButton ? searchButtonWidth + 4 : 0));
            searchBox = addRenderableWidget(new EditBox(font, controlsLeft, searchTop, searchWidth, 18,
                    DSKeyLang.ScreenSearch.copy()));
            searchBox.setHint(DSKeyLang.ScreenSearch.copy());
            if(showSearchButton) {
                addRenderableWidget(Button.builder(DSKeyLang.ScreenSearch.copy(), button -> requestFirstPage())
                        .bounds(controlsLeft + searchWidth + 4, searchTop,
                                searchButtonWidth, 18).build());
            }
        }
        List<ScreenLayout.Bounds> footerBounds = footer.bounds();
        ScreenLayout.Bounds backBounds = footerBounds.get(0);
        ScreenLayout.Bounds previousBounds = footerBounds.get(1);
        ScreenLayout.Bounds nextBounds = footerBounds.get(2);
        ScreenLayout.Bounds instancesBounds = footerBounds.get(3);
        ScreenLayout.Bounds selectBounds = footerBounds.get(4);
        setButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenSelect.copy(), button -> select())
                .bounds(selectBounds.x(), selectBounds.y(), selectBounds.width(), selectBounds.height()).build());
        instancesButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenInstances.copy(), button -> openInstances())
                .bounds(instancesBounds.x(), instancesBounds.y(), instancesBounds.width(), instancesBounds.height()).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> minecraft.setScreen(parent))
                .bounds(backBounds.x(), backBounds.y(), backBounds.width(), backBounds.height()).build());
        previousButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenPrevious.copy(), button -> previousPage())
                .bounds(previousBounds.x(), previousBounds.y(), previousBounds.width(), previousBounds.height()).build());
        nextButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenNext.copy(), button -> nextPage())
                .bounds(nextBounds.x(), nextBounds.y(), nextBounds.width(), nextBounds.height()).build());
        requestFirstPage();
        refreshActions();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        EntityChoicePage page = ClientStats.entityChoices();
        if(page != null && page != adopted && page.requestId() == requestId
                && page.slot() == slot && page.typeFilter().equals(Optional.ofNullable(typeFilter))) {
            boolean invalidated = page.snapshotId() == 0 && !cursor.isEmpty();
            if(invalidated) {
                cursorHistory.clear();
                requestFirstPage();
                return;
            }
            if(page.requestId() == recentRequestId) {
                if(searchValue().isBlank()) {
                    recentChoices = page.allowed()
                            ? page.entries().stream()
                            .filter(entry -> entry.selector() instanceof EntitySelector.Type)
                            .limit(RECENT_MENU_ROWS)
                            .toList()
                            : List.of();
                    if(recentChoices.isEmpty()) recentMenuOpen = false;
                }
                recentRequestId = -1;
            }
            adopted = page;
            cursor = page.cursor();
            selected = null;
            rowOffset = 0;
            refreshActions();
        }
        if(page != null && (page.requestId() != requestId || page.slot() != slot
                || !page.typeFilter().equals(Optional.ofNullable(typeFilter)))) page = null;
        int titleY = Math.min(10, Math.max(0, footerFlow().top() - 1));
        if(footerFlow().top() >= 12) {
            graphics.drawCenteredString(font, title, width / 2,
                    Math.clamp(titleY, 0, Math.max(0, height - 1)), 0xFFFFFFFF);
        }
        rowTooltip = List.of();
        if(page == null || !page.allowed()) {
            if(visibleRows() > 0) {
                graphics.drawCenteredString(font, DSKeyLang.NoData.copy(), width / 2, listTop(), 0xFFAAAAAA);
            }
        } else {
            renderRows(graphics, page, mouseX, mouseY);
        }
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        renderRecentMenu(graphics, mouseX, mouseY);
        if(!rowTooltip.isEmpty()) graphics.renderComponentTooltip(font, rowTooltip, mouseX, mouseY);
    }

    private void renderRows(GuiGraphics graphics, EntityChoicePage page, int mouseX, int mouseY) {
        int rows = Math.min(visibleRows(), page.entries().size() - rowOffset);
        int bottom = listBottom();
        if(bottom <= listTop()) return;
        int left = contentLeft();
        int right = contentRight();
        graphics.enableScissor(left, listTop(), right, bottom);
        for (int row = 0; row < rows; row++) {
            EntityChoiceView entry = page.entries().get(rowOffset + row);
            int y = listTop() + row * ROW_HEIGHT;
            if(isInsideRow(mouseX, mouseY, y)) graphics.fill(left, y, right, y + ROW_HEIGHT - 1, ROW_HOVER);
            if(entry == selected) graphics.fill(left, y, right, y + ROW_HEIGHT - 1, 0x4066CCFF);
            int textWidth = Math.max(1, right - left - 4);
            graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), textWidth),
                    left + 2, y + 2, 0xFFFFFFFF);
            if(!entry.detail().getString().isEmpty()) {
                graphics.drawString(font, font.plainSubstrByWidth(entry.detail().getString(), textWidth),
                        left + 2, y + 12, 0xFFAAAAAA);
            }
            if(isInsideRow(mouseX, mouseY, y)
                    && (font.width(entry.name()) > textWidth || font.width(entry.detail()) > textWidth)) {
                rowTooltip = entry.detail().getString().isEmpty()
                        ? List.of(entry.name()) : List.of(entry.name(), entry.detail());
            }
        }
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(button == 0 && recentMenuOpen) {
            EntityChoiceView recent = recentEntryAt(mouseX, mouseY);
            if(recent != null) {
                selected = recent;
                recentMenuOpen = false;
                select();
                return true;
            }
            if(isInsideRecentMenu(mouseX, mouseY)) {
                recentMenuOpen = false;
                return true;
            }
            if(recentButton != null && recentButton.isMouseOver(mouseX, mouseY)) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
            recentMenuOpen = false;
        }
        if(button == 0 && adopted != null) {
            for (int row = 0; row < Math.min(visibleRows(), adopted.entries().size() - rowOffset); row++) {
                int y = listTop() + row * ROW_HEIGHT;
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
        if(recentMenuOpen) return true;
        if(adopted == null || mouseY < listTop() || mouseY >= listBottom()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int max = Math.max(0, adopted.entries().size() - visibleRows());
        rowOffset = Math.clamp(rowOffset - (int) Math.signum(scrollY), 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == 256 && recentMenuOpen) {
            recentMenuOpen = false;
            return true;
        }
        if(keyCode == 257 || keyCode == 335) {
            requestFirstPage();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void requestFirstPage() {
        cursorHistory.clear();
        boolean requestsRecentTypes = typeFilter == null && searchValue().isBlank();
        recentMenuOpen = false;
        int sentRequestId = request("");
        recentRequestId = requestsRecentTypes ? sentRequestId : -1;
    }

    private void toggleRecentMenu() {
        if(recentChoices.isEmpty()) return;
        recentMenuOpen = !recentMenuOpen;
    }

    private void nextPage() {
        if(adopted == null || !adopted.hasNext() || adopted.nextCursor().isEmpty()) return;
        cursorHistory.add(cursor);
        request(adopted.nextCursor());
    }

    private void previousPage() {
        if(cursorHistory.isEmpty()) return;
        request(cursorHistory.removeLast());
    }

    private int request(String newCursor) {
        cursor = newCursor == null ? "" : newCursor;
        adopted = null;
        selected = null;
        rowOffset = 0;
        recentRequestId = -1;
        requestId = ClientStats.nextEntityChoiceRequestId();
        PacketDistributor.sendToServer(new EntityChoiceRequestPacket(slot, Optional.ofNullable(typeFilter), searchValue(),
                cursor, requestId, contextFilter));
        refreshActions();
        return requestId;
    }

    private String searchValue() {
        return searchBox == null ? "" : searchBox.getValue();
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
        if(previousButton != null) previousButton.active = !cursorHistory.isEmpty();
        if(nextButton != null) nextButton.active = adopted != null && adopted.hasNext();
        if(recentButton != null) {
            recentButton.active = !recentChoices.isEmpty();
            if(!recentButton.active) recentMenuOpen = false;
        }
    }

    private void renderRecentMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        if(!recentMenuOpen || recentButton == null) return;
        RecentMenuLayout layout = recentMenuLayout();
        List<EntityChoiceView> entries = recentEntries(layout);
        if(entries.isEmpty()) return;
        int left = layout.left();
        int top = layout.top();
        int right = left + layout.width();
        int bottom = top + layout.height();
        graphics.fill(left, top, right, bottom, 0xF020262C);
        for(int index = 0; index < entries.size(); index++) {
            EntityChoiceView entry = entries.get(index);
            int y = top + index * ROW_HEIGHT;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if(hovered) graphics.fill(left, y, right, y + ROW_HEIGHT, ROW_HOVER);
            int horizontalPadding = Math.min(3, Math.max(0, (layout.width() - 1) / 2));
            int textWidth = Math.max(0, layout.width() - horizontalPadding * 2);
            if(textWidth > 0) {
                graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), textWidth),
                        left + horizontalPadding, y + 6, 0xFFFFFFFF);
            }
            if(hovered && font.width(entry.name()) > textWidth) rowTooltip = List.of(entry.name());
        }
        graphics.fill(left, top, right, Math.min(bottom, top + 1), 0xFF505A64);
        graphics.fill(left, Math.max(top, bottom - 1), right, bottom, 0xFF505A64);
        graphics.fill(left, top, Math.min(right, left + 1), bottom, 0xFF505A64);
        graphics.fill(Math.max(left, right - 1), top, right, bottom, 0xFF505A64);
    }

    private List<EntityChoiceView> recentEntries(RecentMenuLayout layout) {
        if(layout.rows() == 0) return List.of();
        return recentChoices.subList(0, layout.rows());
    }

    private @Nullable EntityChoiceView recentEntryAt(double mouseX, double mouseY) {
        RecentMenuLayout layout = recentMenuLayout();
        if(!isInsideRecentMenu(mouseX, mouseY, layout)) return null;
        int index = (int) ((mouseY - layout.top()) / ROW_HEIGHT);
        List<EntityChoiceView> entries = recentEntries(layout);
        return index >= 0 && index < entries.size() ? entries.get(index) : null;
    }

    private boolean isInsideRecentMenu(double mouseX, double mouseY) {
        return isInsideRecentMenu(mouseX, mouseY, recentMenuLayout());
    }

    private boolean isInsideRecentMenu(double mouseX, double mouseY, RecentMenuLayout layout) {
        return mouseX >= layout.left() && mouseX < layout.left() + layout.width()
                && mouseY >= layout.top() && mouseY < layout.top() + layout.height();
    }

    private RecentMenuLayout recentMenuLayout() {
        int availableWidth = Math.max(1, contentRight() - contentLeft());
        int menuWidth = Math.min(RECENT_MENU_WIDTH, availableWidth);
        int preferredLeft = recentButton == null ? contentLeft() : recentButton.getX();
        int menuLeft = Math.clamp(preferredLeft, contentLeft(), contentRight() - menuWidth);
        if(recentButton == null || typeFilter != null || recentChoices.isEmpty()) {
            return new RecentMenuLayout(menuLeft, 0, menuWidth, 0);
        }
        int desiredRows = Math.min(RECENT_MENU_ROWS, recentChoices.size());
        int buttonTop = recentButton.getY();
        int belowTop = buttonTop + recentButton.getHeight() + 2;
        int aboveBottom = Math.max(0, buttonTop - 2);
        int belowRows = Math.min(desiredRows, Math.max(0, height - belowTop) / ROW_HEIGHT);
        int aboveRows = Math.min(desiredRows, aboveBottom / ROW_HEIGHT);
        if(belowRows >= aboveRows) return new RecentMenuLayout(menuLeft, belowTop, menuWidth, belowRows);
        return new RecentMenuLayout(menuLeft, aboveBottom - aboveRows * ROW_HEIGHT, menuWidth, aboveRows);
    }

    private int visibleRows() {
        return Math.max(0, (listBottom() - listTop() - 6) / ROW_HEIGHT);
    }

    private boolean isInsideRow(double mouseX, double mouseY, int y) {
        return mouseX >= contentLeft() && mouseX <= contentRight() && mouseY >= y && mouseY < y + ROW_HEIGHT - 1
                && y >= listTop() && y + ROW_HEIGHT - 1 <= listBottom();
    }

    private int listTop() {
        int searchTop = searchTop();
        int preferredTop = searchTop >= 0 ? searchTop + 28 : 24;
        int bottom = listBottom();
        if(bottom - preferredTop < ROW_HEIGHT + 6) return bottom;
        return Math.clamp(preferredTop, 0, Math.max(0, bottom - 1));
    }

    private int listBottom() {
        return Math.max(0, footerFlow().top() - 6);
    }

    private int searchTop() {
        int bottom = footerFlow().top();
        return bottom >= 48 ? Math.min(TOP - 28, Math.max(0, bottom - 22)) : -1;
    }

    private int contentLeft() {
        return Math.min(SIDE, Math.max(0, (width - 1) / 2));
    }

    private int contentRight() {
        return Math.max(contentLeft() + 1, width - contentLeft());
    }

    private int contentWidth() {
        return Math.max(1, width - contentLeft() * 2);
    }

    private ScreenLayout.Flow footerFlow() {
        return ScreenLayout.bottomFlow(width, height, SIDE, 20, 4, 6, 80, 72, 72, 106, 108);
    }

    private record RecentMenuLayout(int left, int top, int width, int rows) {
        private int height() {
            return rows * ROW_HEIGHT;
        }
    }

    private static Component titleFor(FocusSelectionSlot slot) {
        return switch (slot) {
            case SOURCE -> DSKeyLang.FilterSource.get(Component.empty());
            case TARGET -> DSKeyLang.FilterTarget.get(Component.empty());
            case DIRECT_SOURCE -> DSKeyLang.FilterDirect.get(Component.empty());
        };
    }
}
