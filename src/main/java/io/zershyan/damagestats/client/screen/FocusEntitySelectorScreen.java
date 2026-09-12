package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.EntityChoiceRequestPacket;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import io.zershyan.damagestats.stats.view.EntityChoicePage;
import io.zershyan.damagestats.stats.view.EntityChoiceSort;
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
    private static final int ROW_HEIGHT = 24;
    private static final int TOP = 58;
    private static final int SIDE = 24;
    private static final int BACKGROUND = 0xFF15181C;
    private static final int ROW_HOVER = 0x40FFFFFF;
    private static final int RECENT_MENU_ROWS = 8;
    private static final int RECENT_MENU_WIDTH = 220;
    private static final int ACTION_GAP = 4;
    private static final int ACTION_HEIGHT = 18;
    private static final int INSTANCES_MIN_WIDTH = 56;

    private final Screen parent;
    private final Screen selectionReturnScreen;
    private final FocusSelectionSlot slot;
    private final @Nullable ResourceLocation typeFilter;
    private final BiConsumer<EntitySelector, Component> selectionConsumer;
    private final StatsFilter contextFilter;
    private String cursor = "";
    private final List<String> cursorHistory = new ArrayList<>();
    private int requestId;
    private int rowOffset;
    private @Nullable EntityChoicePage adopted;
    private @Nullable EditBox searchBox;
    private @Nullable Button recentButton;
    private @Nullable Button sortButton;
    private EntityChoiceSort sort = EntityChoiceSort.DAMAGE;
    private boolean recentMenuOpen;
    private List<EntityChoiceView> recentChoices = List.of();
    private int recentRequestId = -1;
    private Button previousButton;
    private Button nextButton;
    private final List<Button> rowSelectButtons = new ArrayList<>();
    private final List<Button> rowInstancesButtons = new ArrayList<>();
    private List<Component> rowTooltip = List.of();

    private record RowActionLayout(int selectLeft, int selectWidth, int instancesLeft, int instancesWidth,
                                   int textRight) {
        private boolean supportsInstances() {
            return instancesWidth >= INSTANCES_MIN_WIDTH;
        }
    }

    public FocusEntitySelectorScreen(Screen parent, FocusSelectionSlot slot,
                                     BiConsumer<EntitySelector, Component> selectionConsumer) {
        this(parent, parent, slot, null, StatsFilter.NONE, selectionConsumer);
    }

    public FocusEntitySelectorScreen(Screen parent, FocusSelectionSlot slot, StatsFilter contextFilter,
                                     BiConsumer<EntitySelector, Component> selectionConsumer) {
        this(parent, parent, slot, null, contextFilter, selectionConsumer);
    }

    private FocusEntitySelectorScreen(Screen parent, Screen selectionReturnScreen, FocusSelectionSlot slot,
                                      @Nullable ResourceLocation typeFilter,
                                      StatsFilter contextFilter, BiConsumer<EntitySelector, Component> selectionConsumer) {
        super(titleFor(slot));
        this.parent = parent;
        this.selectionReturnScreen = selectionReturnScreen;
        this.slot = slot;
        this.typeFilter = typeFilter;
        this.contextFilter = contextFilter;
        this.selectionConsumer = selectionConsumer;
    }

    public static FocusEntitySelectorScreen directSourceInstances(Screen parent, ResourceLocation typeFilter,
                                                                    StatsFilter contextFilter,
                                                                    BiConsumer<EntitySelector, Component> selectionConsumer) {
        return directSourceInstances(parent, parent, typeFilter, contextFilter, selectionConsumer);
    }

    public static FocusEntitySelectorScreen directSourceInstances(Screen parent, Screen selectionReturnScreen,
                                                                    ResourceLocation typeFilter,
                                                                    StatsFilter contextFilter,
                                                                    BiConsumer<EntitySelector, Component> selectionConsumer) {
        return new FocusEntitySelectorScreen(parent, selectionReturnScreen, FocusSelectionSlot.DIRECT_SOURCE,
                typeFilter, contextFilter, selectionConsumer);
    }

    public static FocusEntitySelectorScreen instances(Screen parent, FocusSelectionSlot slot, ResourceLocation typeFilter,
                                                       StatsFilter contextFilter,
                                                       BiConsumer<EntitySelector, Component> selectionConsumer) {
        return instances(parent, parent, slot, typeFilter, contextFilter, selectionConsumer);
    }

    public static FocusEntitySelectorScreen instances(Screen parent, Screen selectionReturnScreen,
                                                       FocusSelectionSlot slot, ResourceLocation typeFilter,
                                                       StatsFilter contextFilter,
                                                       BiConsumer<EntitySelector, Component> selectionConsumer) {
        return new FocusEntitySelectorScreen(parent, selectionReturnScreen, slot, typeFilter,
                contextFilter, selectionConsumer);
    }

    @Override
    protected void init() {
        if(minecraft == null) return;
        recentButton = null;
        sortButton = null;
        searchBox = null;
        rowSelectButtons.clear();
        rowInstancesButtons.clear();
        ScreenLayout.Flow footer = footerFlow();
        int left = contentLeft();
        int available = contentWidth();
        int searchTop = searchTop();
        if(searchTop >= 0) {
            int controlsLeft = left;
            if(typeFilter == null && available >= 2) {
                int controlGap = Math.clamp(available - 2, 0, 4);
                int reservedSearchWidth = Math.clamp((available - controlGap) / 2, 1, 64);
                int preferredRecentWidth = Math.clamp(available / 3, 64, 120);
                int recentWidth = Math.clamp(preferredRecentWidth, 1,
                        Math.max(1, available - controlGap - reservedSearchWidth));
                recentButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenRecent.copy(), button ->
                                toggleRecentMenu())
                        .bounds(controlsLeft, searchTop, recentWidth, 18).build());
                controlsLeft += recentWidth + controlGap;
            } else if(typeFilter != null && available >= 2) {
                int controlGap = Math.clamp(available - 2, 0, 4);
                int reservedSearchWidth = Math.clamp((available - controlGap) / 2, 1, 64);
                int preferredSortWidth = Math.clamp(available / 3, 72, 132);
                int sortWidth = Math.clamp(preferredSortWidth, 1,
                        Math.max(1, available - controlGap - reservedSearchWidth));
                sortButton = addRenderableWidget(Button.builder(sortLabel(), button -> cycleSort())
                        .bounds(controlsLeft, searchTop, sortWidth, 18).build());
                controlsLeft += sortWidth + controlGap;
            }
            int remaining = Math.max(1, contentRight() - controlsLeft);
            boolean showSearchButton = remaining >= 92;
            int searchButtonWidth = showSearchButton ? Math.clamp(remaining / 3, 40, 52) : 0;
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
        addRowActionButtons();

        List<ScreenLayout.Bounds> footerBounds = footer.bounds();
        ScreenLayout.Bounds backBounds = footerBounds.get(0);
        ScreenLayout.Bounds previousBounds = footerBounds.get(1);
        ScreenLayout.Bounds nextBounds = footerBounds.get(2);
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
                && page.slot() == slot && page.typeFilter().equals(Optional.ofNullable(typeFilter))
                && page.sort() == sort) {
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
            rowOffset = 0;
            refreshActions();
        }
        if(page != null && (page.requestId() != requestId || page.slot() != slot
                || !page.typeFilter().equals(Optional.ofNullable(typeFilter)) || page.sort() != sort)) page = null;
        updateRowActions(page);
        int titleY = Math.clamp(footerFlow().top() - 1, 0, 10);
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
        RowActionLayout actions = rowActionLayout();
        graphics.enableScissor(left, listTop(), right, bottom);
        for (int row = 0; row < rows; row++) {
            EntityChoiceView entry = page.entries().get(rowOffset + row);
            int y = listTop() + row * ROW_HEIGHT;
            if(isInsideRow(mouseX, mouseY, y)) graphics.fill(left, y, right, y + ROW_HEIGHT - 1, ROW_HOVER);
            int textWidth = Math.max(1, actions.textRight() - left - 4);
            graphics.drawString(font, font.plainSubstrByWidth(entry.name().getString(), textWidth),
                    left + 2, y + 2, 0xFFFFFFFF);
            if(!entry.detail().getString().isEmpty()) {
                graphics.drawString(font, font.plainSubstrByWidth(entry.detail().getString(), textWidth),
                        left + 2, y + 13, 0xFFAAAAAA);
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
        if(minecraft == null) return super.mouseClicked(mouseX, mouseY, button);
        if(button == 0 && recentMenuOpen) {
            EntityChoiceView recent = recentEntryAt(mouseX, mouseY);
            if(recent != null) {
                recentMenuOpen = false;
                selectionConsumer.accept(recent.selector(), recent.name());
                minecraft.setScreen(selectionReturnScreen);
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(recentMenuOpen) return true;
        EntityChoicePage page = adopted;
        if(page == null || mouseY < listTop() || mouseY >= listBottom()) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int max = Math.max(0, page.entries().size() - visibleRows());
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

    private void cycleSort() {
        if(typeFilter == null) return;
        sort = sort.next();
        if(sortButton != null) sortButton.setMessage(sortLabel());
        requestFirstPage();
    }

    private Component sortLabel() {
        return DSKeyLang.ScreenSort.get(switch(sort) {
            case RECENT -> DSKeyLang.SortRecent.copy();
            case DAMAGE -> DSKeyLang.SortDamage.copy();
            case HITS -> DSKeyLang.SortHits.copy();
        });
    }

    private void toggleRecentMenu() {
        if(recentChoices.isEmpty()) return;
        recentMenuOpen = !recentMenuOpen;
    }

    private void nextPage() {
        EntityChoicePage page = adopted;
        if(page == null || !page.hasNext() || page.nextCursor().isEmpty()) return;
        cursorHistory.add(cursor);
        request(page.nextCursor());
    }

    private void previousPage() {
        if(cursorHistory.isEmpty()) return;
        request(cursorHistory.removeLast());
    }

    private int request(String newCursor) {
        cursor = newCursor == null ? "" : newCursor;
        adopted = null;
        rowOffset = 0;
        recentRequestId = -1;
        requestId = ClientStats.nextEntityChoiceRequestId();
        PacketDistributor.sendToServer(new EntityChoiceRequestPacket(slot, Optional.ofNullable(typeFilter), searchValue(),
                cursor, requestId, sort, contextFilter));
        refreshActions();
        return requestId;
    }

    private String searchValue() {
        return searchBox == null ? "" : searchBox.getValue();
    }

    private void selectRow(int row) {
        if(minecraft == null) return;
        EntityChoiceView entry = entryAtRow(row);
        if(entry == null) return;
        selectionConsumer.accept(entry.selector(), entry.name());
        minecraft.setScreen(selectionReturnScreen);
    }

    private void openInstancesRow(int row) {
        if(minecraft == null) return;
        EntityChoiceView entry = entryAtRow(row);
        if(!(entry != null && entry.selector() instanceof EntitySelector.Type(ResourceLocation typeId))) return;
        minecraft.setScreen(new FocusEntitySelectorScreen(this, selectionReturnScreen, slot, typeId,
                contextFilter, selectionConsumer));
    }

    private @Nullable EntityChoiceView entryAtRow(int row) {
        EntityChoicePage page = adopted;
        if(page == null || row < 0 || row >= Math.min(visibleRows(), page.entries().size() - rowOffset)) {
            return null;
        }
        return page.entries().get(rowOffset + row);
    }

    private void addRowActionButtons() {
        RowActionLayout actions = rowActionLayout();
        int rows = visibleRows();
        for(int row = 0; row < rows; row++) {
            int buttonY = listTop() + row * ROW_HEIGHT + (ROW_HEIGHT - ACTION_HEIGHT) / 2;
            int visibleRow = row;
            rowSelectButtons.add(addRenderableWidget(Button.builder(DSKeyLang.ScreenSelect.copy(),
                            button -> selectRow(visibleRow))
                    .bounds(actions.selectLeft(), buttonY, actions.selectWidth(), ACTION_HEIGHT).build()));
            rowInstancesButtons.add(addRenderableWidget(Button.builder(DSKeyLang.ScreenInstances.copy(),
                            button -> openInstancesRow(visibleRow))
                    .bounds(actions.instancesLeft(), buttonY, actions.instancesWidth(), ACTION_HEIGHT).build()));
        }
    }

    private void updateRowActions(@Nullable EntityChoicePage page) {
        RowActionLayout actions = rowActionLayout();
        int rows = Math.clamp(page == null || !page.allowed() ? 0 : page.entries().size() - rowOffset,
                0, rowSelectButtons.size());
        for(int row = 0; row < rowSelectButtons.size(); row++) {
            Button select = rowSelectButtons.get(row);
            Button instances = rowInstancesButtons.get(row);
            boolean rowVisible = row < rows;
            EntityChoiceView entry = rowVisible && page != null
                    ? page.entries().get(rowOffset + row) : null;
            boolean showInstances = entry != null && actions.supportsInstances() && entry.selector() instanceof EntitySelector.Type;
            select.visible = rowVisible;
            select.active = rowVisible;
            instances.visible = showInstances;
            instances.active = showInstances;
        }
    }

    private void refreshActions() {
        if(previousButton != null) previousButton.active = !cursorHistory.isEmpty();
        EntityChoicePage page = adopted;
        if(nextButton != null) nextButton.active = page != null && page.hasNext();
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
        // 先提交底层列表，避免弹窗背景与列表文字在不同缓冲区中重新排序后发生穿透。
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 1000);
        graphics.fill(left - 1, top - 1, right + 1, bottom + 1, 0xFF111418);
        graphics.fill(left, top, right, bottom, 0xFF20262C);
        graphics.flush();
        for(int index = 0; index < entries.size(); index++) {
            EntityChoiceView entry = entries.get(index);
            int y = top + index * ROW_HEIGHT;
            boolean hovered = mouseX >= left && mouseX < right && mouseY >= y && mouseY < y + ROW_HEIGHT;
            if(hovered) graphics.fill(left, y, right, y + ROW_HEIGHT, ROW_HOVER);
            int horizontalPadding = Math.clamp((layout.width() - 1) / 2, 0, 3);
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
        graphics.flush();
        graphics.pose().popPose();
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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKGROUND);
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
        int belowRows = Math.clamp(desiredRows, 0, Math.max(0, height - belowTop) / ROW_HEIGHT);
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
        return bottom >= 48 ? Math.clamp(bottom - 22, 0, TOP - 28) : -1;
    }

    private int contentLeft() {
        return Math.clamp((width - 1) / 2, 0, SIDE);
    }

    private int contentRight() {
        return Math.max(contentLeft() + 1, width - contentLeft());
    }

    private int contentWidth() {
        return Math.max(1, width - contentLeft() * 2);
    }

    private RowActionLayout rowActionLayout() {
        int left = contentLeft();
        int right = contentRight();
        int available = Math.max(1, right - left);
        int selectWidth = Math.clamp(available / 4, 1, 56);
        int preferredInstancesWidth = Math.clamp(available / 3, 1, 76);
        int instancesWidth = typeFilter == null && preferredInstancesWidth >= INSTANCES_MIN_WIDTH
                ? preferredInstancesWidth : 1;
        int gap = Math.clamp(available - selectWidth - instancesWidth, 0, ACTION_GAP);
        int selectLeft = Math.max(left, right - selectWidth);
        int instancesLeft = Math.max(left, selectLeft - gap - instancesWidth);
        int textRight = Math.max(left + 1,
                instancesWidth >= INSTANCES_MIN_WIDTH ? instancesLeft - gap : selectLeft - gap);
        return new RowActionLayout(selectLeft, selectWidth, instancesLeft, instancesWidth, textRight);
    }

    private ScreenLayout.Flow footerFlow() {
        return ScreenLayout.bottomFlow(width, height, SIDE, 20, 4, 6, 80, 72, 72);
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
