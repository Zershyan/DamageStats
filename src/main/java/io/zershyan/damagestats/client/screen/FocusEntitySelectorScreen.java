package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.ui.*;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.EntityChoiceRequestPacket;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import io.zershyan.damagestats.stats.view.EntityChoicePage;
import io.zershyan.damagestats.stats.view.EntityChoiceSort;
import io.zershyan.damagestats.stats.view.EntityChoiceView;
import net.minecraft.client.gui.GuiGraphics;
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
public class FocusEntitySelectorScreen extends UiScreen {
    private static final int ROW_HEIGHT = 24;
    private static final int ACTION_GAP = 4;
    private static final int ACTION_HEIGHT = 18;
    private static final int INSTANCES_MIN_WIDTH = 56;
    private static final int SEARCH_HEIGHT = 18;
    private static final int RECENT_MENU_ROWS = 8;
    private static final int FOOTER_HEIGHT = 26;

    private final Screen parent;
    private final Screen selectionReturnScreen;
    private final FocusSelectionSlot slot;
    private final @Nullable ResourceLocation typeFilter;
    private final BiConsumer<EntitySelector, Component> selectionConsumer;
    private final StatsFilter contextFilter;
    private String cursor = "";
    private final List<String> cursorHistory = new ArrayList<>();
    private int requestId;
    private final UiScroll rowScroll = new UiScroll();
    private @Nullable EntityChoicePage adopted;
    private @Nullable UiTextField searchBox;
    private @Nullable UiButton recentButton;
    private @Nullable UiButton sortButton;
    private EntityChoiceSort sort = EntityChoiceSort.DAMAGE;
    private @Nullable UiPopupMenu recentMenu;
    private List<EntityChoiceView> recentChoices = List.of();
    private int recentRequestId = -1;
    private @Nullable UiButton previousButton;
    private @Nullable UiButton nextButton;
    private final List<UiButton> rowSelectButtons = new ArrayList<>();
    private final List<UiButton> rowInstancesButtons = new ArrayList<>();
    private List<Component> rowTooltip = List.of();

    private UiLayout.Rect listViewport = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect searchRow = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect backSlot = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect previousSlot = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect nextSlot = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect closeSlot = new UiLayout.Rect(0, 0, 0, 0);

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
        computeLayout();
        addSearchControls();
        addRowActionButtons();
        addFooterButtons();
        requestFirstPage();
        refreshActions();
    }

    private void computeLayout() {
        closeSlot = new UiLayout.Rect(topBar().right() - PAGE_PADDING - 18, topBar().y() + 4, 18,
                Math.max(1, topBar().height() - 8));
        UiLayout.Rect page = new UiLayout.Rect(PAGE_PADDING, topBar().bottom() + 6,
                Math.max(0, width - PAGE_PADDING * 2),
                Math.max(0, height - topBar().bottom() - FOOTER_HEIGHT - 12));
        // 空间不足时直接不摆搜索行，把高度让给列表，和原版的下限判断一致
        searchRow = page.height() >= 48 ? new UiLayout.Rect(page.x(), page.y(), page.width(), SEARCH_HEIGHT)
                : new UiLayout.Rect(0, 0, 0, 0);
        int listTop = searchRow.isEmpty() ? page.y() : searchRow.bottom() + 6;
        listViewport = new UiLayout.Rect(page.x(), listTop, page.width(), Math.max(0, page.bottom() - listTop));

        UiLayout.Rect footer = new UiLayout.Rect(PAGE_PADDING, height - FOOTER_HEIGHT + 4,
                Math.max(0, width - PAGE_PADDING * 2), FOOTER_HEIGHT - 8);
        List<UiLayout.Rect> left = UiLayout.row(footer, 5, 72, 72);
        List<UiLayout.Rect> right = UiLayout.rowRight(footer, 5, 72);
        backSlot = left.get(0);
        previousSlot = left.get(1);
        nextSlot = right.getFirst();
    }

    private void addSearchControls() {
        if(searchRow.isEmpty()) return;
        int available = searchRow.width();
        int left = searchRow.x();
        if(typeFilter == null && available >= 2) {
            int controlGap = Math.clamp(available - 2, 0, 4);
            int reservedSearchWidth = Math.clamp((available - controlGap) / 2, 1, 64);
            int recentWidth = Math.clamp(Math.clamp(available / 3, 64, 120), 1,
                    Math.max(1, available - controlGap - reservedSearchWidth));
            recentButton = addRenderableWidget(UiButton.builder(DSKeyLang.ScreenRecent.copy(), button ->
                            toggleRecentMenu())
                    .bounds(new UiLayout.Rect(left, searchRow.y(), recentWidth, SEARCH_HEIGHT))
                    .variant(UiButton.Variant.SECONDARY).icon(UiDraw::clock)
                    .trailingIcon(FocusEntitySelectorScreen::downChevron).build());
            left += recentWidth + controlGap;
        } else if(typeFilter != null && available >= 2) {
            int controlGap = Math.clamp(available - 2, 0, 4);
            int reservedSearchWidth = Math.clamp((available - controlGap) / 2, 1, 64);
            int sortWidth = Math.clamp(Math.clamp(available / 3, 72, 132), 1,
                    Math.max(1, available - controlGap - reservedSearchWidth));
            sortButton = addRenderableWidget(UiButton.builder(sortLabel(), button -> cycleSort())
                    .bounds(new UiLayout.Rect(left, searchRow.y(), sortWidth, SEARCH_HEIGHT))
                    .variant(UiButton.Variant.SECONDARY).icon(UiDraw::chartLine).build());
            left += sortWidth + controlGap;
        }
        int remaining = Math.max(1, searchRow.right() - left);
        boolean showSearchButton = remaining >= 92;
        int searchButtonWidth = showSearchButton ? Math.clamp(remaining / 3, 40, 52) : 0;
        int searchWidth = Math.max(1, remaining - (showSearchButton ? searchButtonWidth + 4 : 0));
        searchBox = addRenderableWidget(new UiTextField(font,
                new UiLayout.Rect(left, searchRow.y(), searchWidth, SEARCH_HEIGHT),
                DSKeyLang.ScreenSearch.copy()));
        if(showSearchButton) {
            addRenderableWidget(UiButton.builder(DSKeyLang.ScreenSearch.copy(), button -> requestFirstPage())
                    .bounds(new UiLayout.Rect(left + searchWidth + 4, searchRow.y(),
                            searchButtonWidth, SEARCH_HEIGHT))
                    .variant(UiButton.Variant.PRIMARY).build());
        }
    }

    /**
     * 行按钮按「槽位」建：槽位 0 对应当前第一行，槽位与条目、纵向位置都在每帧的
     * {@link #updateRowActions} 里随滚动偏移重算。这样平滑滚动时按钮能跟着行走。
     */
    private void addRowActionButtons() {
        RowActionLayout actions = rowActionLayout();
        for(int slot = 0; slot < slotCount(); slot++) {
            int buttonY = listViewport.y() + slot * ROW_HEIGHT + (ROW_HEIGHT - ACTION_HEIGHT) / 2;
            int slotIndex = slot;
            rowSelectButtons.add(addClippedWidget(UiButton.builder(DSKeyLang.ScreenSelect.copy(),
                            button -> selectRow(slotIndex))
                    .bounds(new UiLayout.Rect(actions.selectLeft(), buttonY, actions.selectWidth(), ACTION_HEIGHT))
                    .variant(UiButton.Variant.SECONDARY).build()));
            rowInstancesButtons.add(addClippedWidget(UiButton.builder(DSKeyLang.ScreenInstances.copy(),
                            button -> openInstancesRow(slotIndex))
                    .bounds(new UiLayout.Rect(actions.instancesLeft(), buttonY, actions.instancesWidth(),
                            ACTION_HEIGHT))
                    .variant(UiButton.Variant.GHOST).build()));
        }
    }

    private void addFooterButtons() {
        addRenderableWidget(UiButton.builder(CommonComponents.GUI_BACK, button -> minecraft.setScreen(parent))
                .bounds(backSlot).variant(UiButton.Variant.SECONDARY).icon(FocusEntitySelectorScreen::leftChevron)
                .build());
        previousButton = addRenderableWidget(UiButton.builder(DSKeyLang.ScreenPrevious.copy(),
                        button -> previousPage())
                .bounds(previousSlot).variant(UiButton.Variant.GHOST).build());
        nextButton = addRenderableWidget(UiButton.builder(DSKeyLang.ScreenNext.copy(), button -> nextPage())
                .bounds(nextSlot).variant(UiButton.Variant.PRIMARY).build());
        addRenderableWidget(UiButton.builder(Component.empty(), button -> minecraft.setScreen(parent))
                .bounds(closeSlot).variant(UiButton.Variant.GHOST).icon(UiDraw::cross).build());
    }

    @Override
    protected void renderContent(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
                adoptRecentChoices(page);
                recentRequestId = -1;
            }
            adopted = page;
            cursor = page.cursor();
            rowScroll.reset();
            refreshActions();
        }
        if(page != null && (page.requestId() != requestId || page.slot() != slot
                || !page.typeFilter().equals(Optional.ofNullable(typeFilter)) || page.sort() != sort)) page = null;
        int entryCount = page == null || !page.allowed() ? 0 : page.entries().size();
        rowScroll.measure(entryCount * ROW_HEIGHT, listViewport.height());
        rowScroll.tick();
        updateRowActions(page);
        clipWidgetsTo(listViewport);
        renderTopBar(graphics, title, null, null, false, 34);
        renderCard(graphics, listViewport.inset(-8, -6), false);
        rowTooltip = List.of();
        if(page == null || !page.allowed()) {
            if(visibleRows() > 0) {
                renderEmptyState(graphics, listViewport, UiDraw::target, DSKeyLang.NoData.copy(),
                        DSKeyLang.ScreenSearchHint.copy());
            }
        } else {
            renderRows(graphics, page, mouseX, mouseY);
        }
        renderFooterHint(graphics);
    }

    /** 行数超出可视区时在右缘给一条滚动指示。 */
    private void renderFooterHint(GuiGraphics graphics) {
        rowScroll.renderIndicator(graphics, new UiLayout.Rect(listViewport.right() - 3, listViewport.y(), 3,
                listViewport.height()));
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if(recentMenu != null) recentMenu.render(graphics, mouseX, mouseY);
        if(!rowTooltip.isEmpty()) graphics.renderComponentTooltip(font, rowTooltip, mouseX, mouseY);
    }

    private void renderRows(GuiGraphics graphics, EntityChoicePage page, int mouseX, int mouseY) {
        if(listViewport.isEmpty()) return;
        RowActionLayout actions = rowActionLayout();
        int offset = rowScroll.offset();
        int first = firstEntry();
        graphics.enableScissor(listViewport.x(), listViewport.y(), listViewport.right(), listViewport.bottom());
        for(int slot = 0; slot < slotCount(); slot++) {
            int index = first + slot;
            if(index < 0 || index >= page.entries().size()) continue;
            EntityChoiceView entry = page.entries().get(index);
            int y = listViewport.y() + index * ROW_HEIGHT - offset;
            if(y + ROW_HEIGHT < listViewport.y() || y > listViewport.bottom()) continue;
            boolean hovered = isInsideRow(mouseX, mouseY, y);
            if(hovered) {
                // 高亮必须落在裁剪区内，否则左右各被切掉一点，圆角只剩半边
                UiDraw.roundedRect(graphics, listViewport.x(), y, listViewport.width(),
                        ROW_HEIGHT - 1F, UiTheme.RADIUS_SM, UiTheme.ROW_HOVER);
            }
            int textLeft = listViewport.x() + 4;
            int textWidth = Math.max(1, actions.textRight() - textLeft - 4);
            UiText.plain(graphics, Component.literal(font.plainSubstrByWidth(entry.name().getString(), textWidth)),
                    textLeft, y + 3, hovered ? UiTheme.TEXT_STRONG : UiTheme.TEXT);
            if(!entry.detail().getString().isEmpty()) {
                UiText.plain(graphics,
                        Component.literal(font.plainSubstrByWidth(entry.detail().getString(), textWidth)),
                        textLeft, y + 14, UiTheme.TEXT_MUTED);
            }
            if(hovered && (font.width(entry.name()) > textWidth || font.width(entry.detail()) > textWidth)) {
                rowTooltip = entry.detail().getString().isEmpty()
                        ? List.of(entry.name()) : List.of(entry.name(), entry.detail());
            }
        }
        graphics.disableScissor();
        graphics.flush();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(minecraft == null) return super.mouseClicked(mouseX, mouseY, button);
        // 菜单先吃掉落在自己身上的点击；点在别处则收起再继续往下传，按钮自己负责重新展开
        if(button == 0 && recentMenu != null && recentMenu.isOpen()
                && !recentMenu.covers(mouseX, mouseY)
                && !(recentButton != null && recentButton.isMouseOver(mouseX, mouseY))) {
            recentMenu.close();
        }
        if(button == 0 && recentMenu != null && recentMenu.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(recentMenu != null && recentMenu.isOpen()) {
            recentMenu.mouseScrolled(mouseX, mouseY, scrollY);
            return true;
        }
        if(!listViewport.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        rowScroll.scrollBy(Math.signum(scrollY), ROW_HEIGHT);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode == 256 && recentMenu != null && recentMenu.isOpen()) {
            recentMenu.close();
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
        if(recentMenu != null) recentMenu.close();
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
        if(recentChoices.isEmpty() || recentButton == null) return;
        boolean wasOpen = recentMenu != null && recentMenu.isOpen();
        // 每次展开都按最新的最近类型重建，关闭状态下点按钮才重新弹出
        rebuildRecentMenu();
        if(!wasOpen && recentMenu != null) {
            recentMenu.open(new UiLayout.Rect(recentButton.getX(), recentButton.getY(),
                    recentButton.getWidth(), recentButton.getHeight()));
        }
    }

    private static void downChevron(GuiGraphics graphics, float centerX, float centerY, float size,
                                    float thickness, int color) {
        UiDraw.chevronVertical(graphics, centerX, centerY, size * 0.6F, thickness, 1, color);
    }

    private static void leftChevron(GuiGraphics graphics, float centerX, float centerY, float size,
                                    float thickness, int color) {
        UiDraw.chevron(graphics, centerX, centerY, size * 0.6F, thickness, -1, color);
    }

    /** 最近类型每帧都可能变化，展开时按当前内容重建一次菜单。 */
    private void rebuildRecentMenu() {
        if(recentButton == null) return;
        List<UiPopupMenu.Item> items = new ArrayList<>(recentChoices.size());
        for(EntityChoiceView choice : recentChoices) {
            items.add(new UiPopupMenu.Item(choice.name(), UiDraw::database, () -> {
                selectionConsumer.accept(choice.selector(), choice.name());
                if(minecraft != null) minecraft.setScreen(selectionReturnScreen);
            }));
        }
        recentMenu = new UiPopupMenu(items, width, height);
    }

    private void adoptRecentChoices(EntityChoicePage page) {
        if(searchValue().isBlank()) {
            recentChoices = page.allowed()
                    ? page.entries().stream()
                    .filter(entry -> entry.selector() instanceof EntitySelector.Type)
                    .limit(RECENT_MENU_ROWS)
                    .toList()
                    : List.of();
            if(recentChoices.isEmpty() && recentMenu != null) recentMenu.close();
        }
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
        rowScroll.reset();
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

    private void selectRow(int slotIndex) {
        if(minecraft == null) return;
        EntityChoiceView entry = entryAtSlot(slotIndex);
        if(entry == null) return;
        selectionConsumer.accept(entry.selector(), entry.name());
        minecraft.setScreen(selectionReturnScreen);
    }

    private void openInstancesRow(int slotIndex) {
        if(minecraft == null) return;
        EntityChoiceView entry = entryAtSlot(slotIndex);
        if(!(entry != null && entry.selector() instanceof EntitySelector.Type(ResourceLocation typeId))) return;
        minecraft.setScreen(new FocusEntitySelectorScreen(this, selectionReturnScreen, slot, typeId,
                contextFilter, selectionConsumer));
    }

    /** 当前偏移对应的第一行条目下标。 */
    private int firstEntry() {
        return Math.max(0, rowScroll.offset() / ROW_HEIGHT);
    }

    private @Nullable EntityChoiceView entryAtSlot(int slot) {
        EntityChoicePage page = adopted;
        if(page == null || slot < 0) return null;
        int index = firstEntry() + slot;
        return index >= 0 && index < page.entries().size() ? page.entries().get(index) : null;
    }

    private void updateRowActions(@Nullable EntityChoicePage page) {
        RowActionLayout actions = rowActionLayout();
        int offset = rowScroll.offset();
        int first = firstEntry();
        for(int slot = 0; slot < rowSelectButtons.size(); slot++) {
            UiButton select = rowSelectButtons.get(slot);
            UiButton instances = rowInstancesButtons.get(slot);
            EntityChoiceView entry = page == null || !page.allowed() ? null : entryAtSlot(slot);
            // 按钮跟着行走，并且只在行真的落在可视区里才出现
            int buttonY = listViewport.y() + (first + slot) * ROW_HEIGHT - offset
                    + (ROW_HEIGHT - ACTION_HEIGHT) / 2;
            boolean rowVisible = entry != null
                    && buttonY + ACTION_HEIGHT > listViewport.y() && buttonY < listViewport.bottom();
            boolean showInstances = rowVisible && actions.supportsInstances()
                    && entry.selector() instanceof EntitySelector.Type;
            select.setY(buttonY);
            select.visible = rowVisible;
            select.active = rowVisible;
            instances.setY(buttonY);
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
            if(!recentButton.active && recentMenu != null) recentMenu.close();
        }
    }

    private int visibleRows() {
        return Math.max(0, (listViewport.height() - 6) / ROW_HEIGHT);
    }

    /** 槽位比整行数多一个，平滑滚动时半露的那一行也有对应的行按钮。 */
    private int slotCount() {
        return visibleRows() + 1;
    }

    private boolean isInsideRow(double mouseX, double mouseY, int y) {
        return mouseX >= listViewport.x() && mouseX <= listViewport.right()
                && mouseY >= y && mouseY < y + ROW_HEIGHT - 1
                && y >= listViewport.y() && y + ROW_HEIGHT - 1 <= listViewport.bottom();
    }

    private RowActionLayout rowActionLayout() {
        int left = listViewport.x();
        int right = listViewport.right();
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

    private static Component titleFor(FocusSelectionSlot slot) {
        return switch(slot) {
            case SOURCE -> DSKeyLang.FilterSource.get(Component.empty());
            case TARGET -> DSKeyLang.FilterTarget.get(Component.empty());
            case DIRECT_SOURCE -> DSKeyLang.FilterDirect.get(Component.empty());
        };
    }
}
