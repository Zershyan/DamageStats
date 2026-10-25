package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.client.ClientFocusPreferences;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.ui.*;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.*;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.filter.*;
import io.zershyan.damagestats.stats.focus.EntityGrouping;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import io.zershyan.damagestats.stats.view.FocusChartInstancesPage;
import io.zershyan.damagestats.stats.view.FocusChartPage;
import io.zershyan.damagestats.stats.view.FocusSummary;
import io.zershyan.damagestats.stats.view.GroupView;
import io.zershyan.damagestats.util.LazyComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** 全屏焦点仪表盘：左侧收纳筛选与视图设置，右侧是一张可下钻、可展开的图表卡片。 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class StatsScreen extends UiScreen {
    private static final int SIDEBAR_WIDTH = 118;
    /** 低于这个宽度就改用顶部换行布局：再窄下去图表卡片会被侧栏压到放不下一行分页签。 */
    private static final int SIDEBAR_MIN_SCREEN_WIDTH = 380;
    private static final int SIDEBAR_MIN_BODY_HEIGHT = 100;
    private static final int ROW_HEIGHT = 18;
    private static final int LABEL_LINE = 11;
    private static final int BAR_HEIGHT = 5;
    private static final int LINE_HEIGHT = 11;
    private static final int CARD_HEADER_HEIGHT = 24;
    private static final int CHIP_ROW_HEIGHT = 16;
    private static final int SIDEBAR_SECTION_HEIGHT = 11;
    private static final int SIDEBAR_ROW_HEIGHT = 16;
    private static final int SIDEBAR_ROW_GAP = 3;
    /** 图表右侧三列的期望宽度。固定列宽 + 右对齐，数字才能在行与行之间对齐 */
    private static final int DAMAGE_COLUMN = 52;
    private static final int SHARE_COLUMN = 34;
    private static final int HITS_COLUMN = 36;
    private static final int FOCUS_STATE_REQUEST_ID = 0;
    private static final ResourceLocation PLAYER_TYPE = ResourceLocation.withDefaultNamespace("player");
    private static final FocusChartDimension[] DIMENSIONS = FocusChartDimension.values();

    private FocusChartDimension dimension = FocusChartDimension.DAMAGE_TYPE;
    private FocusChartScope chartScope = FocusChartScope.LIFETIME;
    private DamageTypeGrouping typeGrouping = DamageTypeGrouping.CATEGORY;
    private EntityGrouping entityGrouping = EntityGrouping.TYPE;
    private String chartCursor = "";
    private final List<String> chartCursorHistory = new ArrayList<>();
    private int chartRequestId;
    private final UiScroll contentScroll = new UiScroll();
    private final UiScroll sidebarScroll = new UiScroll();
    /** 侧栏控件已经搬到的偏移，用来算这一帧还要再挪多少 */
    private int sidebarAppliedOffset;
    private final List<AbstractWidget> sidebarWidgets = new ArrayList<>();
    private final Set<FilterKey> expandedChartParents = new HashSet<>();
    private Optional<EntitySelector> browsingSource = Optional.empty();
    private Optional<EntitySelector> browsingTarget = Optional.empty();
    private Optional<EntitySelector> browsingDirectSource = Optional.empty();
    private Optional<DamageTypeSelector> browsingDamageType = Optional.empty();
    private boolean browsingSourceIsDirectSource;
    private Component browsingSourceName = Component.empty();
    private Component browsingTargetName = Component.empty();
    private Component browsingDirectSourceName = Component.empty();
    private Component browsingDamageTypeName = Component.empty();
    private boolean browsingInitialized;
    private boolean focusStateRequested;
    private boolean dataRequestsStarted;
    private long chartFocusVersion = -1;
    private @Nullable FocusChartPage adopted;
    private List<Component> chartTooltip = List.of();
    private List<Component> headerTooltip = List.of();

    private @Nullable UiButton sourceButton;
    private @Nullable UiButton targetButton;
    private @Nullable UiButton clearSourceButton;
    private @Nullable UiButton clearTargetButton;
    private @Nullable UiButton clearChartFiltersButton;
    private @Nullable UiButton setFocusButton;
    private @Nullable UiSegmented dimensionTabs;
    private @Nullable UiSegmented groupingTabs;
    private @Nullable UiSegmented scopeTabs;
    private @Nullable UiButton refreshButton;
    private @Nullable UiButton previousButton;
    private @Nullable UiButton nextButton;
    private @Nullable UiButton actionsButton;
    private @Nullable UiButton closeButton;

    public StatsScreen() {
        super(DSKeyLang.ScreenTitle.copy());
    }

    public void refreshOnOpen() {
        resetForServerRefresh();
    }

    private void resetForServerRefresh() {
        ClientStats.expectFocusState();
        browsingInitialized = false;
        focusStateRequested = false;
        dataRequestsStarted = false;
        chartFocusVersion = -1;
        chartCursor = new String();
        chartCursorHistory.clear();
        expandedChartParents.clear();
        contentScroll.reset();
        adopted = null;
        browsingSource = Optional.empty();
        browsingTarget = Optional.empty();
        browsingDirectSource = Optional.empty();
        browsingDamageType = Optional.empty();
        browsingSourceIsDirectSource = false;
        browsingSourceName = Component.empty();
        browsingTargetName = Component.empty();
        browsingDirectSourceName = Component.empty();
        browsingDamageTypeName = Component.empty();
        ClientStats.clearChartInstances();
        requestFocusState();
    }

    private void requestFocusState() {
        if(focusStateRequested) return;
        focusStateRequested = true;
        PacketDistributor.sendToServer(new FocusStateRequestPacket(FOCUS_STATE_REQUEST_ID));
    }

    @Override
    protected void init() {
        if(!browsingInitialized) ClientStats.expectFocusState();
        PageLayout layout = layout();
        addSidebarWidgets(layout);
        addCardWidgets(layout);
        addCloseButton(layout);
        if(!focusStateRequested) {
            requestFocusState();
        }
        refreshButtons();
    }

    // ------------------------------------------------------------------ 控件装配

    private void addSidebarWidgets(PageLayout layout) {
        if(minecraft == null) return;
        sidebarWidgets.clear();
        sidebarAppliedOffset = 0;
        UiLayout.Rect sourceRow = layout.row(ROW_SOURCE);
        UiLayout.Rect targetRow = layout.row(ROW_TARGET);
        List<UiLayout.Rect> splitSource = UiLayout.row(sourceRow, 2, UiLayout.FILL, 16);
        List<UiLayout.Rect> splitTarget = UiLayout.row(targetRow, 2, UiLayout.FILL, 16);

        sourceButton = addSidebarWidget(UiButton.builder(Component.empty(), button ->
                        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.SOURCE,
                                browsingFilter(), this::setBrowsingSource)))
                .bounds(splitSource.getFirst()).variant(UiButton.Variant.SECONDARY)
                .align(UiButton.Align.LEFT).icon(UiDraw::target).build());
        clearSourceButton = addSidebarWidget(UiButton.builder(Component.empty(), button -> clearSource())
                .bounds(splitSource.get(1)).variant(UiButton.Variant.GHOST)
                .icon(UiDraw::cross).build());
        targetButton = addSidebarWidget(UiButton.builder(Component.empty(), button ->
                        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.TARGET,
                                browsingFilter(), this::setBrowsingTarget)))
                .bounds(splitTarget.getFirst()).variant(UiButton.Variant.SECONDARY)
                .align(UiButton.Align.LEFT).icon(UiDraw::target).build());
        clearTargetButton = addSidebarWidget(UiButton.builder(Component.empty(), button -> clearTarget())
                .bounds(splitTarget.get(1)).variant(UiButton.Variant.GHOST)
                .icon(UiDraw::cross).build());
        clearChartFiltersButton = addSidebarWidget(UiButton.builder(DSKeyLang.ScreenClearChartFilters.copy(),
                        button -> clearChartFilters())
                .bounds(layout.row(ROW_CHART_FILTER)).variant(UiButton.Variant.GHOST).align(UiButton.Align.LEFT)
                .icon(UiDraw::funnel).build());
        setFocusButton = addSidebarWidget(UiButton.builder(DSKeyLang.ScreenSetFocus.copy(), button ->
                        ClientFocusPreferences.requestFocus(browsingSource, browsingTarget,
                                browsingSourceIsDirectSource))
                .bounds(layout.row(ROW_FOCUS)).variant(UiButton.Variant.PRIMARY).icon(UiDraw::target).build());

        groupingTabs = addSidebarWidget(new UiSegmented(layout.row(ROW_GROUPING), List.of(
                new UiSegmented.Option(DSKeyLang.ScreenGroupingCategory.copy()),
                new UiSegmented.Option(DSKeyLang.ScreenGroupingRegistry.copy())), 0, true,
                index -> {
                    DamageTypeGrouping next = index == 0 ? DamageTypeGrouping.CATEGORY : DamageTypeGrouping.REGISTRY;
                    if(typeGrouping == next) return;
                    typeGrouping = next;
                    requestFirstChart();
                }));
        scopeTabs = addSidebarWidget(new UiSegmented(layout.row(ROW_SCOPE), List.of(
                new UiSegmented.Option(DSKeyLang.ScopeSession.copy()),
                new UiSegmented.Option(DSKeyLang.ScopeLifetime.copy())), 0, true,
                index -> {
                    FocusChartScope next = index == 0 ? FocusChartScope.SESSION : FocusChartScope.LIFETIME;
                    if(chartScope == next) return;
                    chartScope = next;
                    requestFirstChart();
                }));

        actionsButton = addSidebarWidget(UiButton.builder(DSKeyLang.ScreenActions.copy(), button ->
                        minecraft.setScreen(new StatsActionsScreen(this)))
                .bounds(layout.row(ROW_ACTIONS)).variant(UiButton.Variant.OUTLINE)
                .align(UiButton.Align.LEFT).icon(UiDraw::ellipsis).build());
    }

    /** 侧栏控件既要跟着滚动搬位置，也要被裁剪到侧栏范围内，所以统一从这里加。 */
    private <T extends AbstractWidget> T addSidebarWidget(T widget) {
        sidebarWidgets.add(widget);
        return addClippedWidget(widget);
    }

    private void addCardWidgets(PageLayout layout) {
        if(minecraft == null) return;
        List<UiSegmented.Option> options = new ArrayList<>(DIMENSIONS.length);
        for(FocusChartDimension candidate : DIMENSIONS) {
            options.add(new UiSegmented.Option(dimensionLabel(candidate), null, supportsDimension(candidate)));
        }
        // 标签页样式（pill=false）：只有一条底线 + 会滑动的指示条，是网页里最常见的一级导航
        dimensionTabs = addRenderableWidget(new UiSegmented(layout.tabs(), options, dimension.ordinal(), false,
                index -> selectDimension(DIMENSIONS[index])));

        List<UiLayout.Rect> pager = layout.pager();
        refreshButton = addRenderableWidget(UiButton.builder(Component.empty(), button -> requestChart(chartCursor))
                .bounds(pager.get(0)).variant(UiButton.Variant.GHOST).icon(UiDraw::refresh).build());
        previousButton = addRenderableWidget(UiButton.builder(Component.empty(), button -> previousChartPage())
                .bounds(pager.get(1)).variant(UiButton.Variant.GHOST).icon(
                        (graphics, cx, cy, size, thickness, color) ->
                                UiDraw.chevron(graphics, cx, cy, size * 0.6F, thickness, -1, color)).build());
        nextButton = addRenderableWidget(UiButton.builder(Component.empty(), button -> nextChartPage())
                .bounds(pager.get(2)).variant(UiButton.Variant.GHOST).icon(
                        (graphics, cx, cy, size, thickness, color) ->
                                UiDraw.chevron(graphics, cx, cy, size * 0.6F, thickness, 1, color)).build());
    }

    private void addCloseButton(PageLayout layout) {
        if(minecraft == null) return;
        closeButton = addRenderableWidget(UiButton.builder(Component.empty(), button -> onClose())
                .bounds(layout.closeButton()).variant(UiButton.Variant.GHOST).icon(UiDraw::cross).build());
    }

    // ------------------------------------------------------------------ 渲染

    @Override
    protected void renderContent(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if(ClientStats.consumeSnapshotInvalidation()) resetForServerRefresh();
        FocusSummary summary = ClientStats.summary();
        boolean dataReady = prepareData(summary);
        if(dataReady && summary.scope().version() != chartFocusVersion) {
            chartFocusVersion = summary.scope().version();
            requestFirstChart();
        }
        if(dataReady) adoptPages(summary);

        PageLayout layout = layout();
        syncSidebarScroll(layout);
        clipWidgetsTo(layout.sidebarMode() ? layout.sidebarArea() : null);
        chartTooltip = List.of();
        headerTooltip = List.of();
        renderTopBar(graphics, title, browsingInitialized
                        ? (isCurrentFocus(summary) ? DSKeyLang.ScreenCurrentFocus.copy() : DSKeyLang.ScreenBrowsing.copy())
                        : null,
                layout.statusPill(),
                browsingInitialized && isCurrentFocus(summary));
        renderSidebar(graphics, layout);
        renderChartCard(graphics, layout, dataReady, mouseX, mouseY);
        renderHoverTooltips(mouseX, mouseY);
    }

    @Override
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if(!chartTooltip.isEmpty()) graphics.renderComponentTooltip(font, chartTooltip, mouseX, mouseY);
        if(!headerTooltip.isEmpty()) graphics.renderComponentTooltip(font, headerTooltip, mouseX, mouseY);
    }

    private boolean prepareData(FocusSummary summary) {
        if(!browsingInitialized) {
            if(!ClientStats.hasFocusState()) return false;
            initializeBrowsing(summary);
            chartFocusVersion = summary.scope().version();
        }
        if(dataRequestsStarted) return true;
        dataRequestsStarted = true;
        requestFirstChart();
        return true;
    }

    private void adoptPages(FocusSummary summary) {
        FocusChartPage page = ClientStats.chartPage();
        if(page != null && page != adopted && page.requestId() == chartRequestId
                && page.focusVersion() == summary.scope().version()) {
            String requestedCursor = chartCursor;
            if(page.snapshotId() == 0 && !requestedCursor.isEmpty()) {
                chartCursorHistory.clear();
                requestChart("");
            } else {
                adopted = page;
                chartCursor = page.cursor();
                contentScroll.reset();
                refreshButtons();
            }
        }
    }

    /**
     * 侧栏内容比可视高度高时平滑滚动。控件位置每帧按当前偏移搬移——
     * 重建控件（clearWidgets + init）做不到逐帧插值，只能搬。
     */
    private void syncSidebarScroll(PageLayout layout) {
        if(!layout.sidebarMode()) {
            sidebarScroll.measure(0, 0);
            return;
        }
        sidebarScroll.measure(layout.sidebarContentHeight(), layout.sidebarArea().height());
        sidebarScroll.tick();
        int delta = sidebarScroll.offset() - sidebarAppliedOffset;
        if(delta != 0) {
            sidebarAppliedOffset = sidebarScroll.offset();
            for(AbstractWidget widget : sidebarWidgets) {
                widget.setY(widget.getY() - delta);
            }
        }
        // 滚出侧栏的控件只是被裁剪区遮住，本身仍然 visible、仍然会吃掉鼠标事件，
        // 必须按侧栏范围把完全滚出去的那些剔除，否则会挡住底下的东西。
        UiLayout.Rect area = layout.sidebarArea();
        for(AbstractWidget widget : sidebarWidgets) {
            widget.visible = widget.getY() + widget.getHeight() > area.y()
                    && widget.getY() < area.bottom();
        }
    }

    /** 侧栏：分组标题 + 控件，标题跟着当前偏移一起搬。 */
    private void renderSidebar(GuiGraphics graphics, PageLayout layout) {
        if(!layout.sidebarMode()) return;
        int offset = sidebarScroll.offset();
        List<UiLayout.Rect> labels = layout.sectionLabels();
        renderSectionLabel(graphics, labels.get(0).translated(0, -offset), DSKeyLang.ScreenFilter.copy());
        renderSectionLabel(graphics, labels.get(1).translated(0, -offset),
                DSKeyLang.ScreenSectionChart.copy());
        sidebarScroll.renderIndicator(graphics, layout.sidebarScrollTrack());
    }

    /** 图表卡片：页头是维度分页签与翻页器，下面是可滚动、可下钻的条形列表。 */
    private void renderChartCard(GuiGraphics graphics, PageLayout layout, boolean dataReady,
                                 int mouseX, int mouseY) {
        UiLayout.Rect card = layout.card();
        if(card.isEmpty()) return;
        renderCard(graphics, card, false);
        UiLayout.Rect header = layout.cardHeader();
        UiDraw.rect(graphics, header.x() + 1, header.bottom() - 1, header.width() - 2, 1F, UiTheme.BORDER);
        graphics.flush();

        UiLayout.Rect viewport = layout.chartViewport();
        if(viewport.isEmpty()) return;
        if(chartFilterActive()) renderChartChips(graphics, layout.chipRow());
        if(!dataReady) {
            contentScroll.measure(0, 0);
            renderLoading(graphics, viewport, DSKeyLang.ScreenLoading.copy());
            return;
        }

        FocusChartPage page = adopted;
        List<ChartRow> rows = validChartPage(page) ? chartRows(page) : List.of();
        contentScroll.measure(Math.max(1, rows.size() * ROW_HEIGHT), viewport.height());
        contentScroll.tick();
        if(rows.isEmpty()) {
            boolean allowed = page == null || page.allowed();
            renderEmptyState(graphics, viewport, UiDraw::database,
                    allowed ? DSKeyLang.NoData.copy() : DSKeyLang.StatsPrivate.copy(),
                    allowed ? DSKeyLang.ScreenEmptyChartHint.copy() : null);
            return;
        }
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        renderChartRows(graphics, viewport, rows, mouseX, mouseY);
        graphics.disableScissor();
        contentScroll.renderIndicator(graphics, layout.chartScrollTrack());
    }

    /** 图表筛选条件做成只读 chip，清除入口在侧栏，避免同一个动作有两个按钮。 */
    private void renderChartChips(GuiGraphics graphics, UiLayout.Rect row) {
        if(row.isEmpty()) return;
        List<Component> chips = new ArrayList<>(2);
        if(browsingDirectSource.isPresent()) chips.add(DSKeyLang.FilterDirect.get(browsingDirectSourceName));
        if(browsingDamageType.isPresent()) chips.add(DSKeyLang.FilterType.get(browsingDamageTypeName));
        int x = row.x();
        for(Component chip : chips) {
            int width = Math.min(font.width(chip) + 16, Math.max(1, row.right() - x));
            if(width < 18) break;
            UiDraw.roundedRect(graphics, x, row.y(), width, row.height(), UiTheme.RADIUS_MD, UiTheme.ACCENT_FAINT);
            UiDraw.roundedBorder(graphics, x + 0.5F, row.y() + 0.5F, width - 1F, row.height() - 1F,
                    UiTheme.RADIUS_MD, 1F, UiTheme.ACCENT);
            UiDraw.dot(graphics, x + 7F, row.centerY(), 4F, UiTheme.ACCENT_BRIGHT);
            UiText.plain(graphics, truncate(chip, Math.max(1, width - 16)), x + 12,
                    row.centerY() - UiText.lineHeight() / 2 + 1, UiTheme.ACCENT_BRIGHT);
            x += width + 4;
            if(x >= row.right()) break;
        }
        graphics.flush();
    }

    private void renderChartRows(GuiGraphics graphics, UiLayout.Rect viewport, List<ChartRow> rows,
                                 int mouseX, int mouseY) {
        // 三列固定宽度、各自右对齐；窗口太窄时按比例压缩，保证标签还有位置
        float scale = Math.min(1F, (viewport.width() - 46F) / (DAMAGE_COLUMN + SHARE_COLUMN + HITS_COLUMN));
        int hitsColumn = Math.round(HITS_COLUMN * scale);
        int shareColumn = Math.round(SHARE_COLUMN * scale);
        int damageColumn = Math.round(DAMAGE_COLUMN * scale);
        int hitsRight = viewport.right() - 4;
        int shareRight = hitsRight - hitsColumn;
        int damageRight = shareRight - shareColumn;
        float maxDamage = 1F;
        for(ChartRow row : rows) {
            GroupView group = row.group();
            if(group != null) maxDamage = Math.max(maxDamage, group.damage());
        }
        int offset = contentScroll.offset();
        for(int index = 0; index < rows.size(); index++) {
            ChartRow row = rows.get(index);
            int y = viewport.y() + index * ROW_HEIGHT - offset;
            if(y + ROW_HEIGHT < viewport.y() || y > viewport.bottom()) continue;
            boolean hovered = mouseX >= viewport.x() && mouseX <= viewport.right()
                    && mouseY >= y && mouseY < y + ROW_HEIGHT && mouseY >= viewport.y()
                    && mouseY < viewport.bottom();
            if(hovered) {
                UiDraw.roundedRect(graphics, viewport.x(), y, viewport.width(), ROW_HEIGHT - 1F,
                        UiTheme.RADIUS_SM, UiTheme.ROW_HOVER);
                // 行悬停底先提交，行内文字才会画在它之上
                graphics.flush();
            }
            GroupView group = row.group();
            if(group == null) {
                renderMarkerRow(graphics, viewport, row, y);
                continue;
            }
            renderGroupRow(graphics, viewport, row, group, y, damageRight, shareRight, hitsRight,
                    maxDamage, hovered);
            if(hovered) chartTooltip = chartTooltip(group);
        }
        graphics.flush();
    }

    private void renderMarkerRow(GuiGraphics graphics, UiLayout.Rect viewport, ChartRow row, int y) {
        Component marker = switch(row.marker()) {
            case LOADING -> DSKeyLang.ScreenLoading.copy();
            case EMPTY -> row.allowed() ? DSKeyLang.NoData.copy() : DSKeyLang.StatsPrivate.copy();
            case NEXT -> DSKeyLang.ScreenTruncated.copy();
            case NONE -> Component.empty();
        };
        int indent = row.indent() * 12;
        boolean actionable = row.marker() == RowMarker.NEXT;
        UiText.plain(graphics, truncate(marker, Math.max(1, viewport.width() - indent - 8)),
                viewport.x() + indent + 4, y + 4,
                actionable ? UiTheme.ACCENT : UiTheme.TEXT_MUTED);
        if(actionable) {
            UiDraw.chevronVertical(graphics, viewport.right() - 8F, y + ROW_HEIGHT / 2F - 2F, 7F, 1F, 1,
                    UiTheme.ACCENT);
        }
    }

    private void renderGroupRow(GuiGraphics graphics, UiLayout.Rect viewport, ChartRow row, GroupView group,
                                int y, int damageRight, int shareRight, int hitsRight,
                                float maxDamage, boolean hovered) {
        int indent = row.indent() * 12;
        int left = viewport.x() + indent;
        int textY = y + 1;
        boolean expandable = row.indent() == 0 && expandable(group);
        int labelLeft = left + 4;
        if(expandable) {
            UiDraw.caret(graphics, left + 6F, textY + LABEL_LINE / 2F, 7F,
                    expandedChartParents.contains(group.key()) ? 1 : 2, UiTheme.TEXT_MUTED);
            labelLeft = left + 14;
        }
        UiText.plain(graphics, truncate(group.name(), Math.max(1, damageRight - 52 - labelLeft)), labelLeft,
                textY, row.indent() == 0 ? UiTheme.TEXT : UiTheme.TEXT_SECONDARY);
        // 伤害值加粗做主数值，占比与命中次数用弱色做辅助列
        UiText.boldRight(graphics, chartDamage(group), damageRight, textY,
                hovered ? UiTheme.TEXT_STRONG : UiTheme.TEXT);
        UiText.right(graphics, chartShare(group), shareRight, textY, UiTheme.TEXT_MUTED);
        UiText.right(graphics, chartHits(group), hitsRight, textY, UiTheme.TEXT_MUTED);

        int barTop = textY + LABEL_LINE + 1;
        float barWidth = Math.max(1, viewport.width());
        UiDraw.roundedRect(graphics, viewport.x(), barTop, barWidth, BAR_HEIGHT, BAR_HEIGHT / 2F,
                UiTheme.BAR_TRACK);
        int filled = Mth.clamp(Math.round(barWidth * group.damage() / Math.max(1F, maxDamage)), 1, (int) barWidth);
        // 子项用中性灰条，和父项强调色区分开，展开后层级一眼可辨
        int barTopColor = row.indent() == 0
                ? (hovered ? UiTheme.BAR_HOVER_TOP : UiTheme.BAR_TOP)
                : UiTheme.BAR_CHILD_TOP;
        int barBottomColor = row.indent() == 0
                ? (hovered ? UiTheme.BAR_HOVER_BOTTOM : UiTheme.BAR_BOTTOM)
                : UiTheme.BAR_CHILD_BOTTOM;
        UiDraw.roundedRect(graphics, viewport.x(), barTop, filled, BAR_HEIGHT, BAR_HEIGHT / 2F,
                barTopColor, barBottomColor);
    }

    /** 三列各自的文本。格式化沿用 DamageStats 统一的 #.# 规则，和重构前的输出一致。 */
    private static Component chartDamage(GroupView group) {
        return Component.literal(LazyComponent.number1f(group.damage()));
    }

    private static Component chartShare(GroupView group) {
        return Component.literal(LazyComponent.number1f(group.share() * 100) + "%");
    }

    private static Component chartHits(GroupView group) {
        return Component.literal("×" + LazyComponent.number1f(group.hitCount()));
    }

    /** 按钮文字被截断时补一个完整文本提示，避免省略号吃掉信息。 */
    private void renderHoverTooltips(int mouseX, int mouseY) {
        // Arrays.asList 允许元素为 null，控件尚未装配完时不会直接抛异常
        for(UiButton button : Arrays.asList(sourceButton, targetButton, clearSourceButton, clearTargetButton,
                clearChartFiltersButton, setFocusButton, actionsButton, refreshButton, previousButton,
                nextButton, closeButton)) {
            if(button != null && button.visible && button.isMouseOver(mouseX, mouseY)
                    && button.isLabelTruncated()) {
                headerTooltip = List.of(button.getMessage());
                return;
            }
        }
    }

    // ------------------------------------------------------------------ 交互

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PageLayout layout = layout();
        if(layout.sidebarMode() && layout.sidebarArea().contains(mouseX, mouseY)) {
            sidebarScroll.scrollBy(Math.signum(scrollY), SIDEBAR_ROW_HEIGHT + SIDEBAR_ROW_GAP);
            return true;
        }
        UiLayout.Rect viewport = layout.chartViewport();
        UiLayout.Rect card = layout.card();
        // 命中区放宽到整张卡片：图表区左右各有 8px 内边距，鼠标落在上面也应该能滚动
        if(mouseY < viewport.y() || mouseY >= viewport.bottom()
                || mouseX < card.x() || mouseX >= card.right()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        contentScroll.scrollBy(Math.signum(scrollY), LINE_HEIGHT * 2);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(super.mouseClicked(mouseX, mouseY, button)) return true;
        PageLayout layout = layout();
        FocusChartPage page = adopted;
        UiLayout.Rect viewport = layout.chartViewport();
        if((button != 0 && button != 1) || !viewport.contains(mouseX, mouseY)
                || !validChartPage(page)) return false;
        List<ChartRow> rows = chartRows(page);
        int index = (int) ((mouseY - viewport.y() + contentScroll.offset()) / ROW_HEIGHT);
        if(index < 0 || index >= rows.size()) return false;
        ChartRow chartRow = rows.get(index);
        if(chartRow.group() == null) {
            // 占位行只有左键有意义
            if(button != 0) return false;
            if(chartRow.marker() == RowMarker.NEXT && chartRow.parentKey() != null) {
                FocusChartInstancesPage instances = ClientStats.chartInstancesPage(chartRow.parentKey());
                if(validChartInstancesPage(instances, chartRow.parentKey()) && instances.hasNext()) {
                    requestChartInstances(chartRow.parentKey(), instances.nextCursor());
                }
            } else if(chartRow.marker() == RowMarker.NEXT) {
                nextChartPage();
            }
            return true;
        }
        // 右键对实体行开操作菜单。伤害类型行没有对应的实体操作，直接左键下钻即可
        if(button == 1) {
            if(hasEntityActions(chartRow.group())) {
                showEntityActions(chartRow.group().key(), chartRow.group().name());
                return true;
            }
            return false;
        }
        applyChartFilter(chartRow.group());
        return true;
    }

    /** 只有实体维度的行才有操作菜单；伤害类型行没有可设的来源或目标。 */
    private static boolean hasEntityActions(GroupView group) {
        return switch(group.key()) {
            case FilterKey.Source(EntitySelector ignored) -> true;
            case FilterKey.Target(EntitySelector ignored) -> true;
            case FilterKey.Direct(EntitySelector ignored) -> true;
            case FilterKey.Type(DamageTypeSelector ignored) -> false;
        };
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(FocusGuiClosedPacket.INSTANCE);
        super.onClose();
    }

    // ------------------------------------------------------------------ 请求

    private void requestFirstChart() {
        if(!browsingInitialized) return;
        chartCursorHistory.clear();
        requestChart("");
    }

    private void requestChart(String cursor) {
        if(!browsingInitialized) return;
        chartCursor = cursor == null ? "" : cursor;
        adopted = null;
        expandedChartParents.clear();
        ClientStats.clearChartInstances();
        contentScroll.reset();
        chartRequestId = ClientStats.nextChartRequestId();
        PacketDistributor.sendToServer(new FocusChartRequestPacket(browsingFilter(), dimension, chartScope,
                typeGrouping, EntityGrouping.TYPE, chartCursor, chartRequestId));
    }

    private void nextChartPage() {
        FocusChartPage page = adopted;
        if(page == null || !page.hasNext() || page.nextCursor().isEmpty()) return;
        chartCursorHistory.add(chartCursor);
        requestChart(page.nextCursor());
    }

    private void previousChartPage() {
        if(chartCursorHistory.isEmpty()) return;
        requestChart(chartCursorHistory.removeLast());
    }

    private void clearSource() {
        if(!canChooseAnySource()) return;
        browsingSource = Optional.empty();
        browsingSourceName = DSKeyLang.ScreenAllDamage.copy();
        browsingSourceIsDirectSource = false;
        entityGrouping = EntityGrouping.TYPE;
        normalizeDimension();
        requestFirstChart();
    }

    private void clearTarget() {
        browsingTarget = Optional.empty();
        browsingTargetName = DSKeyLang.OverlayAllTargets.copy();
        entityGrouping = EntityGrouping.TYPE;
        normalizeDimension();
        requestFirstChart();
    }

    private void clearChartFilters() {
        browsingDirectSource = Optional.empty();
        browsingDamageType = Optional.empty();
        browsingDirectSourceName = Component.empty();
        browsingDamageTypeName = Component.empty();
        requestFirstChart();
    }

    /** 页头每帧同步一次控件状态，让禁用态与真实数据保持一致。 */
    private void refreshButtons() {
        if(dimensionTabs != null) dimensionTabs.setSelected(dimension.ordinal());
        if(scopeTabs != null) scopeTabs.setSelected(chartScope == FocusChartScope.SESSION ? 0 : 1);
        if(groupingTabs != null) {
            groupingTabs.setSelected(typeGrouping == DamageTypeGrouping.CATEGORY ? 0 : 1);
            groupingTabs.active = browsingInitialized && dimension == FocusChartDimension.DAMAGE_TYPE;
        }
        if(sourceButton != null) {
            sourceButton.setMessage(DSKeyLang.FilterSource.get(browsingInitialized
                    ? browsingSourceName : ClientStats.summary().scope().sourceName()));
            sourceButton.active = browsingInitialized && canChooseAnySource();
        }
        if(targetButton != null) {
            targetButton.setMessage(DSKeyLang.FilterTarget.get(browsingInitialized
                    ? browsingTargetName : ClientStats.summary().scope().targetName()));
            targetButton.active = browsingInitialized;
        }
        if(clearSourceButton != null) {
            clearSourceButton.active = browsingInitialized && canChooseAnySource() && browsingSource.isPresent();
        }
        if(clearTargetButton != null) {
            clearTargetButton.active = browsingInitialized && browsingTarget.isPresent();
        }
        if(clearChartFiltersButton != null) {
            clearChartFiltersButton.active = browsingInitialized && chartFilterActive();
        }
        if(previousButton != null) previousButton.active = !chartCursorHistory.isEmpty();
        FocusChartPage page = adopted;
        if(nextButton != null) nextButton.active = page != null && page.hasNext();
        if(refreshButton != null) refreshButton.active = browsingInitialized;
        if(setFocusButton != null) {
            var scope = ClientStats.summary().scope();
            setFocusButton.active = browsingInitialized
                    && (!scope.source().equals(browsingSource) || !scope.target().equals(browsingTarget)
                    || scope.sourceIsDirectSource() != browsingSourceIsDirectSource);
        }
        if(actionsButton != null) actionsButton.active = browsingInitialized;
    }

    private boolean chartFilterActive() {
        return browsingDirectSource.isPresent() || browsingDamageType.isPresent();
    }

    /** 维度分页签切换。状态迁移与原先的循环切换完全一致：重置实体分组并重新请求首屏。 */
    private void selectDimension(FocusChartDimension candidate) {
        if(dimension == candidate || !supportsDimension(candidate)) return;
        dimension = candidate;
        entityGrouping = EntityGrouping.TYPE;
        requestFirstChart();
    }

    private static Component dimensionLabel(FocusChartDimension candidate) {
        return switch(candidate) {
            case DAMAGE_TYPE -> DSKeyLang.DimensionTypes.copy();
            case DIRECT_SOURCE -> DSKeyLang.DimensionSources.copy();
            case RESPONSIBLE_SOURCE -> DSKeyLang.DimensionAllSources.copy();
            case TARGET -> DSKeyLang.DimensionOpponents.copy();
        };
    }

    private void initializeBrowsing(FocusSummary summary) {
        if(browsingInitialized) return;
        browsingInitialized = true;
        browsingSource = summary.scope().source();
        browsingTarget = summary.scope().target();
        browsingSourceIsDirectSource = summary.scope().sourceIsDirectSource();
        browsingSourceName = summary.scope().sourceName();
        browsingTargetName = summary.scope().targetName();
        refreshButtons();
    }

    StatsFilter browsingFilter() {
        return new StatsFilter(browsingSource, browsingTarget, browsingDirectSource, browsingDamageType,
                browsingSourceIsDirectSource);
    }

    private void setBrowsingSource(EntitySelector selector, Component name) {
        browsingSource = Optional.of(selector);
        browsingSourceName = name;
        browsingSourceIsDirectSource = false;
        entityGrouping = EntityGrouping.TYPE;
        normalizeDimension();
        requestFirstChart();
    }

    private void setBrowsingTarget(EntitySelector selector, Component name) {
        browsingTarget = Optional.of(selector);
        browsingTargetName = name;
        entityGrouping = EntityGrouping.TYPE;
        normalizeDimension();
        requestFirstChart();
    }

    private void setBrowsingDirectSource(EntitySelector selector, Component name) {
        if(isPlayerSelector(selector)) {
            browsingDirectSource = Optional.empty();
            browsingDirectSourceName = Component.empty();
            setBrowsingSource(selector, name);
            return;
        }
        browsingSource = Optional.of(selector);
        browsingSourceName = name;
        browsingSourceIsDirectSource = true;
        entityGrouping = EntityGrouping.TYPE;
        browsingDirectSource = Optional.empty();
        browsingDirectSourceName = Component.empty();
        normalizeDimension();
        requestFirstChart();
    }

    /** 点击图表行时的下钻：能展开的先展开实例，否则把该行设成筛选条件。 */
    private void applyChartFilter(GroupView group) {
        switch(group.key()) {
            case FilterKey.Source(EntitySelector selector) -> {
                if(selector instanceof EntitySelector.Type && group.canOpenInstances()) {
                    toggleChartInstances(group);
                } else {
                    setBrowsingSource(selector, group.name());
                }
            }
            case FilterKey.Target(EntitySelector selector) -> {
                if(selector instanceof EntitySelector.Type && group.canOpenInstances()) {
                    toggleChartInstances(group);
                } else {
                    setBrowsingTarget(selector, group.name());
                }
            }
            case FilterKey.Direct(EntitySelector.Type selector) -> {
                if(group.canOpenInstances()) {
                    toggleChartInstances(group);
                } else {
                    toggleDirectFilter(selector, group.name());
                }
            }
            case FilterKey.Direct(EntitySelector.Instance selector) -> setBrowsingDirectSource(selector, group.name());
            case FilterKey.Type(DamageTypeSelector.Category selector) ->
                    drillDamageTypeCategory(selector, group.name());
            case FilterKey.Type(DamageTypeSelector.Exact selector) ->
                    toggleDamageTypeFilter(selector, group.name());
        }
    }

    private void toggleChartInstances(GroupView group) {
        if(!expandable(group)) return;
        FilterKey parentKey = group.key();
        if(!expandedChartParents.add(parentKey)) {
            expandedChartParents.remove(parentKey);
            return;
        }
        FocusChartInstancesPage page = ClientStats.chartInstancesPage(parentKey);
        if(!validChartInstancesPage(page, parentKey)) requestChartInstances(parentKey, "");
    }

    private void requestChartInstances(FilterKey parentKey, String cursor) {
        if(!browsingInitialized) return;
        int requestId = ClientStats.nextChartInstancesRequestId(parentKey);
        PacketDistributor.sendToServer(new FocusChartInstancesRequestPacket(browsingFilter(), dimension,
                chartScope, typeGrouping, parentKey, cursor == null ? "" : cursor, requestId));
    }

    private static <T> Optional<T> toggle(Optional<T> current, T selected) {
        return current.filter(selected::equals).isPresent() ? Optional.empty() : Optional.of(selected);
    }

    void showEntityActions(FilterKey key, Component name) {
        if(minecraft == null) return;
        minecraft.setScreen(new ChartEntityActionScreen(this, key, name));
    }

    void selectAsSource(EntitySelector selector, Component name) {
        setBrowsingSource(selector, name);
    }

    void selectAsTarget(EntitySelector selector, Component name) {
        setBrowsingTarget(selector, name);
    }

    void selectAsDirectSource(EntitySelector selector, Component name) {
        setBrowsingDirectSource(selector, name);
    }

    void toggleDirectFilter(EntitySelector selector, Component name) {
        boolean clearing = browsingDirectSource.filter(selector::equals).isPresent();
        browsingDirectSource = toggle(browsingDirectSource, selector);
        browsingDirectSourceName = clearing ? Component.empty() : name;
        requestFirstChart();
    }

    private void toggleDamageTypeFilter(DamageTypeSelector selector, Component name) {
        boolean clearing = browsingDamageType.filter(selector::equals).isPresent();
        browsingDamageType = toggle(browsingDamageType, selector);
        browsingDamageTypeName = clearing ? Component.empty() : name;
        requestFirstChart();
    }

    private void drillDamageTypeCategory(DamageTypeSelector.Category selector, Component name) {
        browsingDamageType = Optional.of(selector);
        browsingDamageTypeName = name;
        typeGrouping = DamageTypeGrouping.REGISTRY;
        requestFirstChart();
    }

    void openInstances(EntitySelector.Type type, FilterKey key, Screen backScreen) {
        if(minecraft == null) return;
        if(key instanceof FilterKey.Direct) {
            minecraft.setScreen(FocusEntitySelectorScreen.directSourceInstances(backScreen, this, type.typeId(),
                    browsingFilter(), this::selectAsDirectSource));
            return;
        }
        FocusSelectionSlot slot = key instanceof FilterKey.Source ? FocusSelectionSlot.SOURCE : FocusSelectionSlot.TARGET;
        minecraft.setScreen(FocusEntitySelectorScreen.instances(backScreen, this, slot, type.typeId(), browsingFilter(),
                slot == FocusSelectionSlot.SOURCE ? this::selectAsSource : this::selectAsTarget));
    }

    boolean canChooseAnySource() {
        if(minecraft == null) return false;
        var player = minecraft.player;
        return player != null && (minecraft.hasSingleplayerServer() || player.hasPermissions(2));
    }

    void confirmReset(boolean global) {
        PacketDistributor.sendToServer(global ? ResetStatsPacket.GLOBAL : ResetStatsPacket.INSTANCE);
    }

    void openResetConfirmation() {
        if(minecraft == null) return;
        minecraft.setScreen(new ConfirmResetScreen(this));
    }

    void openGlobalResetConfirmation() {
        if(minecraft == null) return;
        if(!canResetAll()) return;
        minecraft.setScreen(new ConfirmResetScreen(this, true));
    }

    boolean canResetAll() {
        if(minecraft == null) return false;
        var player = minecraft.player;
        return player != null && (minecraft.hasSingleplayerServer() || player.hasPermissions(2));
    }

    void openOverlay() {
        if(minecraft == null) return;
        minecraft.setScreen(new OverlayPositionScreen(this));
    }

    void openDetails() {
        if(minecraft == null) return;
        minecraft.setScreen(new StatsDetailsScreen(this));
    }

    void openHistory() {
        if(minecraft == null) return;
        minecraft.setScreen(new StatsHistoryScreen(this));
    }

    void requestExport() {
        if(ClientExportManager.isBusy()) return;
        int requestId = ClientExportManager.nextRequestId();
        PacketDistributor.sendToServer(new ExportRequestPacket(browsingFilter(), typeGrouping, requestId));
    }

    private boolean isCurrentFocus(FocusSummary summary) {
        return summary.scope().source().equals(browsingSource)
                && summary.scope().target().equals(browsingTarget)
                && summary.scope().sourceIsDirectSource() == browsingSourceIsDirectSource;
    }

    private static boolean isPlayerSelector(EntitySelector selector) {
        return switch(selector) {
            case EntitySelector.Instance(EntityRef ref) -> PLAYER_TYPE.equals(ref.typeId());
            case EntitySelector.Type(ResourceLocation typeId) -> PLAYER_TYPE.equals(typeId);
        };
    }

    private void normalizeDimension() {
        if(supportsDimension(dimension)) return;
        for(FocusChartDimension candidate : DIMENSIONS) {
            if(supportsDimension(candidate)) {
                dimension = candidate;
                return;
            }
        }
    }

    private boolean supportsDimension(FocusChartDimension candidate) {
        return switch(candidate) {
            case DAMAGE_TYPE, DIRECT_SOURCE -> true;
            case RESPONSIBLE_SOURCE -> browsingSource.isEmpty() && browsingTarget.isPresent();
            case TARGET -> browsingSource.isPresent() && browsingTarget.isEmpty();
        };
    }

    private boolean validChartPage(@Nullable FocusChartPage page) {
        return page != null && page.requestId() == chartRequestId
                && page.focusVersion() == ClientStats.summary().scope().version() && page.allowed();
    }

    private boolean validChartInstancesPage(@Nullable FocusChartInstancesPage page, FilterKey parentKey) {
        return page != null && page.focusVersion() == ClientStats.summary().scope().version()
                && page.dimension() == dimension && page.scope() == chartScope
                && page.typeGrouping() == typeGrouping && page.filter().equals(browsingFilter())
                && page.parentKey().equals(parentKey);
    }

    private List<ChartRow> chartRows(FocusChartPage page) {
        List<ChartRow> rows = new ArrayList<>();
        for(GroupView group : page.rows()) {
            rows.add(new ChartRow(group, 0, null, RowMarker.NONE, true));
            if(!expandedChartParents.contains(group.key())) continue;
            FocusChartInstancesPage instances = ClientStats.chartInstancesPage(group.key());
            if(!validChartInstancesPage(instances, group.key())) {
                rows.add(new ChartRow(null, 1, group.key(), RowMarker.LOADING, true));
                continue;
            }
            if(!instances.allowed() || instances.rows().isEmpty()) {
                rows.add(new ChartRow(null, 1, group.key(), RowMarker.EMPTY, instances.allowed()));
                continue;
            }
            instances.rows().forEach(child -> rows.add(new ChartRow(child, 1, null, RowMarker.NONE, true)));
            if(instances.hasNext()) {
                rows.add(new ChartRow(null, 1, group.key(), RowMarker.NEXT, true));
            }
        }
        if(page.hasNext()) rows.add(new ChartRow(null, 0, null, RowMarker.NEXT, true));
        return rows;
    }

    private static boolean expandable(GroupView group) {
        if(!group.canOpenInstances()) return false;
        return switch(group.key()) {
            case FilterKey.Source(EntitySelector.Type ignored) -> true;
            case FilterKey.Target(EntitySelector.Type ignored) -> true;
            case FilterKey.Direct(EntitySelector.Type ignored) -> true;
            default -> false;
        };
    }

    private static List<Component> chartTooltip(GroupView group) {
        float reduced = Math.max(0, group.originalDamage() - group.damage());
        float rate = group.originalDamage() <= 0 ? 0 : reduced / group.originalDamage() * 100;
        List<Component> lines = new ArrayList<>(7);
        lines.add(group.name());
        lines.add(DSKeyLang.TotalDamage.getNumber1f(group.damage()));
        lines.add(DSKeyLang.OriginalDamage.getNumber1f(group.originalDamage()));
        lines.add(DSKeyLang.ReductionRate.getNumber1f(reduced, rate));
        lines.add(DSKeyLang.HitCount.get(group.hitCount()));
        lines.add(DSKeyLang.DetailLine.getNumber1f(Component.empty(), group.damage(), group.share() * 100,
                group.hitCount()));
        // 右键入口没有任何视觉提示，只能在悬停说明里点一句，否则没人会发现
        if(hasEntityActions(group)) lines.add(DSKeyLang.ScreenRightClickHint.copy());
        return lines;
    }

    // ------------------------------------------------------------------ 布局

    private static final int ROW_SOURCE = 0;
    private static final int ROW_TARGET = 1;
    private static final int ROW_CHART_FILTER = 2;
    private static final int ROW_FOCUS = 3;
    private static final int ROW_GROUPING = 4;
    private static final int ROW_SCOPE = 5;
    private static final int ROW_ACTIONS = 6;
    private static final int ROW_COUNT = 7;
    /** 窄屏自动换行时每一块的期望宽度，顺序与上面的行号一致。 */
    private static final int[] COMPACT_WIDTHS = {136, 136, 118, 118, 96, 96, 96};

    /**
     * 页面分栏。宽屏走「侧栏 + 图表卡片」，窄屏退化成控件在上、卡片在下——
     * 侧栏在窄窗口里会被压成一条缝，换行铺开比硬撑可读得多。
     */
    private PageLayout layout() {
        UiLayout.Rect page = new UiLayout.Rect(PAGE_PADDING, topBar().bottom() + 6,
                Math.max(0, width - PAGE_PADDING * 2),
                Math.max(0, height - topBar().bottom() - 12));
        boolean sidebarMode = width >= SIDEBAR_MIN_SCREEN_WIDTH && page.height() >= SIDEBAR_MIN_BODY_HEIGHT;

        List<UiLayout.Rect> rows = new ArrayList<>(ROW_COUNT);
        List<UiLayout.Rect> sectionLabels = new ArrayList<>(2);
        UiLayout.Rect sidebarArea;
        UiLayout.Rect card;
        int contentHeight = 0;
        if(sidebarMode) {
            sidebarArea = page.takeLeft(SIDEBAR_WIDTH);
            card = page.dropLeft(SIDEBAR_WIDTH, 8);
            int y = sidebarArea.y();
            sectionLabels.add(new UiLayout.Rect(sidebarArea.x(), y, sidebarArea.width(), SIDEBAR_SECTION_HEIGHT));
            y += SIDEBAR_SECTION_HEIGHT + SIDEBAR_ROW_GAP;
            for(int index = 0; index < 4; index++) {
                rows.add(new UiLayout.Rect(sidebarArea.x(), y, sidebarArea.width(), SIDEBAR_ROW_HEIGHT));
                y += SIDEBAR_ROW_HEIGHT + SIDEBAR_ROW_GAP;
            }
            y += 5;
            sectionLabels.add(new UiLayout.Rect(sidebarArea.x(), y, sidebarArea.width(), SIDEBAR_SECTION_HEIGHT));
            y += SIDEBAR_SECTION_HEIGHT + SIDEBAR_ROW_GAP;
            rows.add(new UiLayout.Rect(sidebarArea.x(), y, sidebarArea.width(), SIDEBAR_ROW_HEIGHT));
            y += SIDEBAR_ROW_HEIGHT + SIDEBAR_ROW_GAP;
            rows.add(new UiLayout.Rect(sidebarArea.x(), y, sidebarArea.width(), SIDEBAR_ROW_HEIGHT));
            y += SIDEBAR_ROW_HEIGHT + SIDEBAR_ROW_GAP + 5;
            rows.add(new UiLayout.Rect(sidebarArea.x(), y, sidebarArea.width(), SIDEBAR_ROW_HEIGHT + 2));
            y += SIDEBAR_ROW_HEIGHT + 2;
            contentHeight = y - sidebarArea.y();
        } else {
            List<UiLayout.Rect> wrapped = UiLayout.wrap(page, 3, SIDEBAR_ROW_HEIGHT, COMPACT_WIDTHS);
            rows.addAll(wrapped);
            sidebarArea = new UiLayout.Rect(page.x(), page.y(), 0, 0);
            card = page.dropTop(wrapped.getLast().bottom() - page.y() + 7, 0);
            sectionLabels.add(new UiLayout.Rect(0, 0, 0, 0));
            sectionLabels.add(new UiLayout.Rect(0, 0, 0, 0));
        }

        UiLayout.Rect cardHeader = card.takeTop(CARD_HEADER_HEIGHT);
        List<UiLayout.Rect> pager = UiLayout.row(cardHeader.takeRight(68).inset(3, 3), 2, 20, 20, 20);
        UiLayout.Rect tabs = cardHeader.dropRight(70, 2).inset(4, 3);
        UiLayout.Rect chipRow = new UiLayout.Rect(card.x() + 8, cardHeader.bottom() + 3,
                Math.max(0, card.width() - 16), CHIP_ROW_HEIGHT);
        int chipsOffset = chartFilterActive() ? CHIP_ROW_HEIGHT + 3 : 0;
        int viewportTop = cardHeader.bottom() + 4 + chipsOffset;
        UiLayout.Rect viewport = new UiLayout.Rect(card.x() + 8, viewportTop,
                Math.max(0, card.width() - 16), Math.max(0, card.bottom() - 4 - viewportTop));
        UiLayout.Rect scrollTrack = new UiLayout.Rect(card.right() - 6, viewport.y(), 3, viewport.height());

        int statusWidth = Math.max(font.width(DSKeyLang.ScreenCurrentFocus.copy()),
                font.width(DSKeyLang.ScreenBrowsing.copy())) + 20;
        UiLayout.Rect close = new UiLayout.Rect(topBar().right() - PAGE_PADDING - 18, topBar().y() + 4,
                18, Math.max(1, topBar().height() - 8));
        UiLayout.Rect status = new UiLayout.Rect(Math.max(PAGE_PADDING, close.x() - 6 - statusWidth),
                topBar().y() + 4, statusWidth, Math.max(1, topBar().height() - 8));

        return new PageLayout(sidebarMode, sidebarArea, card, cardHeader, tabs, pager, chipRow, viewport,
                scrollTrack, rows, sectionLabels, contentHeight, status, close);
    }

    private record PageLayout(boolean sidebarMode, UiLayout.Rect sidebarArea, UiLayout.Rect card,
                              UiLayout.Rect cardHeader, UiLayout.Rect tabs, List<UiLayout.Rect> pager,
                              UiLayout.Rect chipRow, UiLayout.Rect chartViewport, UiLayout.Rect chartScrollTrack,
                              List<UiLayout.Rect> rows, List<UiLayout.Rect> sectionLabels,
                              int sidebarContentHeight, UiLayout.Rect statusPill, UiLayout.Rect closeButton) {
        private UiLayout.Rect row(int index) {
            return rows.get(index);
        }

        private UiLayout.Rect sidebarScrollTrack() {
            return new UiLayout.Rect(sidebarArea.right() - 3, sidebarArea.y(), 3, sidebarArea.height());
        }
    }

    private enum RowMarker { NONE, LOADING, EMPTY, NEXT }

    private record ChartRow(@Nullable GroupView group, int indent, @Nullable FilterKey parentKey,
                            RowMarker marker, boolean allowed) {}
}
