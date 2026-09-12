package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.client.ClientFocusPreferences;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.*;
import io.zershyan.damagestats.stats.filter.*;
import io.zershyan.damagestats.stats.focus.EntityGrouping;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import io.zershyan.damagestats.stats.view.FocusChartPage;
import io.zershyan.damagestats.stats.view.FocusSummary;
import io.zershyan.damagestats.stats.view.GroupView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 全屏焦点仪表盘：页头即时更新，页间统一滚动，图表和历史数据按需请求。 */
public class StatsScreen extends Screen {
    private static final int SIDE = 14;
    private static final int LINE_HEIGHT = 12;
    private static final int ROW_HEIGHT = 20;
    private static final int CONTENT_GAP = 8;
    private static final int MIN_CONTENT_HEIGHT = 30;
    private static final int FOCUS_STATE_REQUEST_ID = 0;
    private static final int BACKGROUND = 0xFF15181C;
    private static final int HEADER = 0xFF20262C;
    private static final int BORDER = 0xFF505A64;
    private static final int BAR = 0xFF2F8FA3;
    private static final int BAR_HOVER = 0xFF49B4C5;
    private static final int MUTED = 0xFFACB8C2;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF55D6E8;

    private FocusChartDimension dimension = FocusChartDimension.DAMAGE_TYPE;
    private FocusChartScope chartScope = FocusChartScope.LIFETIME;
    private DamageTypeGrouping typeGrouping = DamageTypeGrouping.CATEGORY;
    private EntityGrouping entityGrouping = EntityGrouping.TYPE;
    private String chartCursor = "";
    private final List<String> chartCursorHistory = new ArrayList<>();
    private int chartRequestId;
    private int contentScrollOffset;
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
    private List<Component> chartValues = List.of();
    private List<Component> chartTooltip = List.of();
    private List<Component> headerTooltip = List.of();
    private @Nullable Button sourceButton;
    private @Nullable Button targetButton;
    private @Nullable Button dimensionButton;
    private @Nullable Button scopeButton;
    private @Nullable Button groupingButton;
    private @Nullable Button setFocusButton;
    private @Nullable Button actionsButton;
    private @Nullable Button clearSourceButton;
    private @Nullable Button clearTargetButton;
    private @Nullable Button clearChartFiltersButton;
    private @Nullable Button previousButton;
    private @Nullable Button nextButton;
    private Button refreshButton;
    private @Nullable Button exportButton;

    public StatsScreen() {
        super(DSKeyLang.ScreenTitle.copy());
    }

    @Override
    protected void init() {
        if(!browsingInitialized) ClientStats.expectFocusState();
        PageLayout layout = layout();
        addHeaderButtons(layout);
        addFooterButtons(layout);
        if(!focusStateRequested) {
            focusStateRequested = true;
            PacketDistributor.sendToServer(new FocusStateRequestPacket(FOCUS_STATE_REQUEST_ID));
        }
        refreshButtons();
    }

    private void addHeaderButtons(PageLayout layout) {
        ScreenLayout.Bounds sourceBounds = layout.topRow().get(0);
        ScreenLayout.Bounds targetBounds = layout.topRow().get(1);
        ScreenLayout.Bounds focusBounds = layout.topRow().get(2);
        ScreenLayout.Bounds actionsBounds = layout.topRow().get(3);
        sourceButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.SOURCE,
                                browsingFilter(), (selector, name) -> setBrowsingSource(selector, name))))
                .bounds(sourceBounds.x(), sourceBounds.y(), sourceBounds.width(), sourceBounds.height()).build());
        targetButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.TARGET,
                                browsingFilter(), (selector, name) -> setBrowsingTarget(selector, name))))
                    .bounds(targetBounds.x(), targetBounds.y(), targetBounds.width(), targetBounds.height()).build());
        setFocusButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenSetFocus.copy(), button ->
                        ClientFocusPreferences.requestFocus(browsingSource, browsingTarget,
                                browsingSourceIsDirectSource))
                        .bounds(focusBounds.x(), focusBounds.y(), focusBounds.width(), focusBounds.height()).build());
        actionsButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenActions.copy(), button ->
                        minecraft.setScreen(new StatsActionsScreen(this)))
                .bounds(actionsBounds.x(), actionsBounds.y(), actionsBounds.width(), actionsBounds.height()).build());

        ScreenLayout.Bounds clearSourceBounds = layout.middleRow().get(0);
        ScreenLayout.Bounds clearTargetBounds = layout.middleRow().get(1);
        ScreenLayout.Bounds overlayBounds = layout.middleRow().get(2);
        ScreenLayout.Bounds exportBounds = layout.middleRow().get(3);
        ScreenLayout.Bounds clearFiltersBounds = layout.middleRow().get(4);
        clearSourceButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearSource.copy(), button -> clearSource())
                .bounds(clearSourceBounds.x(), clearSourceBounds.y(), clearSourceBounds.width(), clearSourceBounds.height()).build());
        clearTargetButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearTarget.copy(), button -> clearTarget())
                .bounds(clearTargetBounds.x(), clearTargetBounds.y(), clearTargetBounds.width(), clearTargetBounds.height()).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenEditOverlay.copy(), button -> openOverlay())
                    .bounds(overlayBounds.x(), overlayBounds.y(), overlayBounds.width(), overlayBounds.height()).build());
        exportButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenExport.copy(), button -> requestExport())
                    .bounds(exportBounds.x(), exportBounds.y(), exportBounds.width(), exportBounds.height()).build());
        clearChartFiltersButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearChartFilters.copy(),
                        button -> clearChartFilters())
                    .bounds(clearFiltersBounds.x(), clearFiltersBounds.y(), clearFiltersBounds.width(), clearFiltersBounds.height()).build());

        ScreenLayout.Bounds dimensionBounds = layout.toolbar().get(0);
        ScreenLayout.Bounds scopeBounds = layout.toolbar().get(1);
        ScreenLayout.Bounds groupingBounds = layout.toolbar().get(2);
        ScreenLayout.Bounds refreshBounds = layout.toolbar().get(3);
        ScreenLayout.Bounds previousBounds = layout.toolbar().get(4);
        ScreenLayout.Bounds nextBounds = layout.toolbar().get(5);
        dimensionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    cycleDimension();
                    requestFirstChart();
                })
                    .bounds(dimensionBounds.x(), dimensionBounds.y(), dimensionBounds.width(), dimensionBounds.height()).build());
        scopeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    chartScope = chartScope == FocusChartScope.SESSION
                            ? FocusChartScope.LIFETIME : FocusChartScope.SESSION;
                    requestFirstChart();
                })
                    .bounds(scopeBounds.x(), scopeBounds.y(), scopeBounds.width(), scopeBounds.height()).build());
        groupingButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    typeGrouping = typeGrouping.next();
                    requestFirstChart();
                })
                    .bounds(groupingBounds.x(), groupingBounds.y(), groupingBounds.width(), groupingBounds.height()).build());
        refreshButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenRefresh.copy(), button -> requestChart(chartCursor))
                    .bounds(refreshBounds.x(), refreshBounds.y(), refreshBounds.width(), refreshBounds.height()).build());
        previousButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenPrevious.copy(), button -> previousChartPage())
                    .bounds(previousBounds.x(), previousBounds.y(), previousBounds.width(), previousBounds.height()).build());
        nextButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenNext.copy(), button -> nextChartPage())
                    .bounds(nextBounds.x(), nextBounds.y(), nextBounds.width(), nextBounds.height()).build());
    }

    private void addFooterButtons(PageLayout layout) {
        FooterLayout footer = layout.footer();
        if(footer.reset() != null) {
            ScreenLayout.Bounds resetBounds = footer.reset();
            addRenderableWidget(Button.builder(DSKeyLang.ScreenReset.copy(), button -> openResetConfirmation())
                    .bounds(resetBounds.x(), resetBounds.y(), resetBounds.width(), resetBounds.height()).build());
        }
        if(footer.resetAll() != null) {
            ScreenLayout.Bounds resetAllBounds = footer.resetAll();
            addRenderableWidget(Button.builder(DSKeyLang.ScreenResetAll.copy(), button -> openGlobalResetConfirmation())
                    .bounds(resetAllBounds.x(), resetAllBounds.y(), resetAllBounds.width(), resetAllBounds.height()).build());
        }
        ScreenLayout.Bounds doneBounds = footer.done();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(doneBounds.x(), doneBounds.y(), doneBounds.width(), doneBounds.height()).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        FocusSummary summary = ClientStats.summary();
        boolean dataReady = prepareData(summary);
        if(dataReady && summary.scope().version() != chartFocusVersion) {
            chartFocusVersion = summary.scope().version();
            requestFirstChart();
        }
        if(dataReady) adoptPages(summary);

        PageLayout layout = layout();
        chartTooltip = List.of();
        headerTooltip = List.of();
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, layout.headerHeight(), HEADER);
        graphics.fill(0, layout.footerTop(), width, height, HEADER);
        if(layout.headerHeight() > 0) graphics.fill(0, layout.headerHeight() - 1, width, layout.headerHeight(), BORDER);
        if(layout.footerTop() < height) graphics.fill(0, layout.footerTop(), width, layout.footerTop() + 1, BORDER);

        renderHeader(graphics, summary, layout, mouseX, mouseY);
        if(dataReady) renderContent(graphics, adopted, mouseX, mouseY, layout);
        else renderLoading(graphics, layout);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
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

    private void renderLoading(GuiGraphics graphics, PageLayout layout) {
        int top = contentTop(layout);
        int bottom = contentBottom(layout);
        if(bottom <= top) return;
        graphics.drawCenteredString(font, DSKeyLang.ScreenLoading.copy(), width / 2,
                top + Math.max(0, (bottom - top - font.lineHeight) / 2), MUTED);
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
                chartValues = chartValues(page.rows());
                chartCursor = page.cursor();
                contentScrollOffset = 0;
                refreshButtons();
            }
        }
    }

    private void renderHeader(GuiGraphics graphics, FocusSummary summary, PageLayout layout, int mouseX, int mouseY) {
        Component sourceName = browsingInitialized ? browsingSourceName : summary.scope().sourceName();
        Component targetName = browsingInitialized ? browsingTargetName : summary.scope().targetName();
        if(sourceButton != null) {
            sourceButton.setMessage(DSKeyLang.FilterSource.get(sourceName));
            appendButtonTooltip(sourceButton, mouseX, mouseY);
        }
        if(targetButton != null) {
            targetButton.setMessage(DSKeyLang.FilterTarget.get(targetName));
            appendButtonTooltip(targetButton, mouseX, mouseY);
        }
        Component state = isCurrentFocus(summary) ? DSKeyLang.ScreenCurrentFocus.copy() : DSKeyLang.ScreenBrowsing.copy();
        int left = ScreenLayout.left(width, SIDE);
        int right = ScreenLayout.right(width, SIDE);
        int titleWidth = font.width(title);
        int stateWidth = font.width(state);
        if(layout.headerHeight() >= 14 && right - left >= titleWidth + stateWidth + 12) {
            graphics.drawString(font, title, left, 4, TEXT);
            graphics.drawString(font, state, right - stateWidth, 4, ACCENT);
        } else if(layout.headerHeight() >= 14) {
            graphics.drawCenteredString(font, truncate(title, Math.max(1, right - left)), width / 2, 4, TEXT);
        }
        refreshButtons();
        renderables.forEach(renderable -> {
            if(renderable instanceof Button button) appendButtonTooltip(button, mouseX, mouseY);
        });
    }

    private void renderContent(GuiGraphics graphics, @Nullable FocusChartPage page,
                               int mouseX, int mouseY, PageLayout layout) {
        int top = contentTop(layout);
        int bottom = contentBottom(layout);
        if(bottom <= top) return;
        int chartOffset = 0;
        int totalHeight = chartBlockHeight(page) + CONTENT_GAP;
        int viewportHeight = bottom - top;
        contentScrollOffset = Mth.clamp(contentScrollOffset, 0, Math.max(0, totalHeight - viewportHeight));

        int left = ScreenLayout.left(width, SIDE);
        int right = ScreenLayout.right(width, SIDE);
        graphics.enableScissor(Math.max(0, left - 2), top, Math.min(width, right + 2), bottom);
        renderChartAt(graphics, page, chartOffset, mouseX, mouseY, layout);
        graphics.disableScissor();
    }

    private void renderChartAt(GuiGraphics graphics, @Nullable FocusChartPage page, int contentOffset,
                               int mouseX, int mouseY, PageLayout layout) {
        int left = ScreenLayout.left(width, SIDE);
        int right = ScreenLayout.right(width, SIDE);
        int screenTop = contentTop(layout) + contentOffset - contentScrollOffset;
        graphics.drawString(font, truncate(dimensionLabel(), Math.max(1, right - left)), left, screenTop, ACCENT);
        int rowTop = screenTop + 16;
        if(!validChartPage(page)) {
            graphics.drawString(font, page != null && !page.allowed() ? DSKeyLang.StatsPrivate.copy()
                    : DSKeyLang.NoData.copy(), left, rowTop, MUTED);
            return;
        }
        List<GroupView> rows = page.rows();
        if(rows.isEmpty()) {
            graphics.drawString(font, DSKeyLang.NoData.copy(), left, rowTop, MUTED);
            return;
        }
        int contentWidth = Math.max(1, right - left);
        int labelWidth = Math.min(Math.max(36, contentWidth / 3), Math.max(1, contentWidth - 24));
        int barLeft = Math.min(right - 1, left + labelWidth + 8);
        int barWidth = Math.max(1, right - barLeft);
        float maxDamage = rows.stream().map(GroupView::damage).max(Float::compare).orElse(1f);
        for (int row = 0; row < rows.size(); row++) {
            GroupView group = rows.get(row);
            int y = rowTop + row * ROW_HEIGHT;
            int widthForDamage = Mth.clamp(Math.round(barWidth * group.damage() / Math.max(1, maxDamage)), 1, barWidth);
            boolean hovered = mouseX >= left && mouseX <= right
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 2
                    && mouseY >= contentTop(layout) && mouseY < contentBottom(layout);
            graphics.fill(barLeft, y + 2, barLeft + widthForDamage, y + ROW_HEIGHT - 3,
                    hovered ? BAR_HOVER : BAR);
            graphics.drawString(font, truncate(group.name(), labelWidth), left, y + 5, TEXT);
            Component value = chartValues.get(row);
            graphics.drawString(font, truncate(value, Math.max(1, barWidth - 4)), barLeft + 2, y + 5, TEXT);
            if(hovered) chartTooltip = chartTooltip(group);
        }
        if(page.hasNext()) {
            int y = rowTop + rows.size() * ROW_HEIGHT;
            graphics.drawString(font, DSKeyLang.ScreenTruncated.copy(), left, y, MUTED);
        }
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PageLayout layout = layout();
        if(mouseY < contentTop(layout) || mouseY >= contentBottom(layout)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int totalHeight = contentHeight(adopted);
        int viewportHeight = Math.max(1, contentBottom(layout) - contentTop(layout));
        int step = Math.max(1, Math.round((float) Math.abs(scrollY) * LINE_HEIGHT));
        if(scrollY > 0) step = -step;
        contentScrollOffset = Mth.clamp(contentScrollOffset + step, 0,
                Math.max(0, totalHeight - viewportHeight));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(super.mouseClicked(mouseX, mouseY, button)) return true;
        PageLayout layout = layout();
        if(button != 0 || !insideContent(mouseX, mouseY, layout) || !validChartPage(adopted)) return false;
        int chartOffset = 0;
        int rowTop = contentTop(layout) + chartOffset + 16 - contentScrollOffset;
        int chartBottom = rowTop + adopted.rows().size() * ROW_HEIGHT;
        if(mouseY < rowTop || mouseY >= chartBottom) return false;
        int row = (int) ((mouseY - rowTop) / ROW_HEIGHT);
        if(row < 0 || row >= adopted.rows().size()) return false;
        applyChartFilter(adopted.rows().get(row));
        return true;
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(FocusGuiClosedPacket.INSTANCE);
        super.onClose();
    }

    private void requestFirstChart() {
        if(!browsingInitialized) return;
        chartCursorHistory.clear();
        requestChart("");
    }

    private void requestChart(String cursor) {
        if(!browsingInitialized) return;
        chartCursor = cursor == null ? "" : cursor;
        adopted = null;
        chartValues = List.of();
        contentScrollOffset = 0;
        chartRequestId = ClientStats.nextChartRequestId();
        PacketDistributor.sendToServer(new FocusChartRequestPacket(browsingFilter(), dimension, chartScope,
                typeGrouping, entityGrouping, chartCursor, chartRequestId));
    }

    private void nextChartPage() {
        if(adopted == null || !adopted.hasNext() || adopted.nextCursor().isEmpty()) return;
        chartCursorHistory.add(chartCursor);
        requestChart(adopted.nextCursor());
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

    private void refreshButtons() {
        if(dimensionButton != null) dimensionButton.setMessage(dimensionLabel());
        if(scopeButton != null) scopeButton.setMessage(chartScope == FocusChartScope.SESSION
                ? DSKeyLang.ScopeSession.copy() : DSKeyLang.ScopeLifetime.copy());
        if(groupingButton != null) {
            groupingButton.visible = true;
            groupingButton.active = browsingInitialized && dimension == FocusChartDimension.DAMAGE_TYPE;
            groupingButton.setMessage(typeGrouping == DamageTypeGrouping.CATEGORY
                    ? DSKeyLang.ScreenGroupingCategory.copy() : DSKeyLang.ScreenGroupingRegistry.copy());
        }
        if(refreshButton != null) refreshButton.setMessage(DSKeyLang.ScreenRefresh.copy());
        if(previousButton != null) previousButton.active = !chartCursorHistory.isEmpty();
        if(nextButton != null) nextButton.active = adopted != null && adopted.hasNext();
        if(sourceButton != null) sourceButton.active = browsingInitialized && canChooseAnySource();
        if(targetButton != null) targetButton.active = browsingInitialized;
        if(clearSourceButton != null) clearSourceButton.active = browsingInitialized && canChooseAnySource()
                && browsingSource.isPresent();
        if(clearTargetButton != null) clearTargetButton.active = browsingInitialized && browsingTarget.isPresent();
        if(clearChartFiltersButton != null) {
            clearChartFiltersButton.active = browsingInitialized
                    && (browsingDirectSource.isPresent() || browsingDamageType.isPresent());
        }
        if(setFocusButton != null) {
            var scope = ClientStats.summary().scope();
            setFocusButton.active = browsingInitialized
                    && (!scope.source().equals(browsingSource) || !scope.target().equals(browsingTarget)
                    || scope.sourceIsDirectSource() != browsingSourceIsDirectSource);
        }
        if(exportButton != null) exportButton.active = !ClientExportManager.isBusy();
    }

    private Component dimensionLabel() {
        return switch (dimension) {
            case DAMAGE_TYPE -> DSKeyLang.DimensionTypes.copy();
            case DIRECT_SOURCE -> DSKeyLang.DimensionSources.copy();
            case RESPONSIBLE_SOURCE -> DSKeyLang.FilterSource.get(DSKeyLang.ViewAll.copy());
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
        browsingSource = Optional.of(selector);
        browsingSourceName = name;
        browsingSourceIsDirectSource = true;
        entityGrouping = EntityGrouping.TYPE;
        browsingDirectSource = Optional.empty();
        browsingDirectSourceName = Component.empty();
        normalizeDimension();
        requestFirstChart();
    }

    private void applyChartFilter(GroupView group) {
        switch (group.key()) {
            case FilterKey.Source(EntitySelector selector) -> {
                if(selector instanceof EntitySelector.Type && entityGrouping == EntityGrouping.TYPE
                        && group.canOpenInstances()) {
                    drillEntityInstances();
                } else {
                    setBrowsingSource(selector, group.name());
                }
            }
            case FilterKey.Target(EntitySelector selector) -> {
                if(selector instanceof EntitySelector.Type && entityGrouping == EntityGrouping.TYPE
                        && group.canOpenInstances()) {
                    drillEntityInstances();
                } else {
                    setBrowsingTarget(selector, group.name());
                }
            }
            case FilterKey.Direct(EntitySelector.Type selector) -> {
                if(entityGrouping == EntityGrouping.TYPE && group.canOpenInstances()) {
                    drillEntityInstances();
                } else {
                    toggleDirectFilter(selector, group.name());
                }
            }
            case FilterKey.Direct(EntitySelector.Instance selector) -> toggleDirectFilter(selector, group.name());
            case FilterKey.Type(DamageTypeSelector.Category selector) ->
                    drillDamageTypeCategory(selector, group.name());
            case FilterKey.Type(DamageTypeSelector.Exact selector) ->
                    toggleDamageTypeFilter(selector, group.name());
        }
    }

    private void drillEntityInstances() {
        entityGrouping = EntityGrouping.INSTANCE;
        requestFirstChart();
    }

    private static <T> Optional<T> toggle(Optional<T> current, T selected) {
        return current.filter(selected::equals).isPresent() ? Optional.empty() : Optional.of(selected);
    }

    void showEntityActions(FilterKey key, Component name) {
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
        return minecraft.player != null && (minecraft.hasSingleplayerServer() || minecraft.player.hasPermissions(2));
    }

    void confirmReset(boolean global) {
        PacketDistributor.sendToServer(global ? ResetStatsPacket.GLOBAL : ResetStatsPacket.INSTANCE);
    }

    void openResetConfirmation() {
        minecraft.setScreen(new ConfirmResetScreen(this));
    }

    void openGlobalResetConfirmation() {
        if(!canResetAll()) return;
        minecraft.setScreen(new ConfirmResetScreen(this, true));
    }

    boolean canResetAll() {
        return minecraft.player != null && (minecraft.hasSingleplayerServer() || minecraft.player.hasPermissions(2));
    }

    void openOverlay() {
        minecraft.setScreen(new OverlayPositionScreen(this));
    }

    void openDetails() {
        minecraft.setScreen(new StatsDetailsScreen(this));
    }

    void openHistory() {
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

    private void renderChartFilters(GuiGraphics graphics, int y) {
        MutableComponent line = Component.empty();
        if(browsingDirectSource.isPresent()) line.append(DSKeyLang.FilterDirect.get(browsingDirectSourceName));
        if(browsingDamageType.isPresent()) {
            if(!line.getString().isEmpty()) line.append(Component.literal("  "));
            line.append(DSKeyLang.FilterType.get(browsingDamageTypeName));
        }
        if(line.getString().isEmpty()) return;
        int left = ScreenLayout.left(width, SIDE);
        graphics.drawString(font, truncate(line, Math.max(1, ScreenLayout.width(width, SIDE))), left, y, MUTED);
    }

    private void cycleDimension() {
        FocusChartDimension[] values = FocusChartDimension.values();
        for (int offset = 1; offset <= values.length; offset++) {
            FocusChartDimension candidate = values[(dimension.ordinal() + offset) % values.length];
            if(supportsDimension(candidate)) {
                dimension = candidate;
                entityGrouping = EntityGrouping.TYPE;
                return;
            }
        }
    }

    private void normalizeDimension() {
        if(supportsDimension(dimension)) return;
        for (FocusChartDimension candidate : FocusChartDimension.values()) {
            if(supportsDimension(candidate)) {
                dimension = candidate;
                return;
            }
        }
    }

    private boolean supportsDimension(FocusChartDimension candidate) {
        return switch (candidate) {
            case DAMAGE_TYPE, DIRECT_SOURCE -> true;
            case RESPONSIBLE_SOURCE -> browsingSource.isEmpty() && browsingTarget.isPresent();
            case TARGET -> browsingSource.isPresent() && browsingTarget.isEmpty();
        };
    }

    private boolean validChartPage(@Nullable FocusChartPage page) {
        return page != null && page.requestId() == chartRequestId
                && page.focusVersion() == ClientStats.summary().scope().version() && page.allowed();
    }

    private int contentHeight(@Nullable FocusChartPage page) {
        return chartBlockHeight(page) + CONTENT_GAP;
    }

    private int chartBlockHeight(@Nullable FocusChartPage page) {
        int rows = validChartPage(page) ? Math.max(1, page.rows().size()) : 1;
        int truncated = validChartPage(page) && page.hasNext() ? LINE_HEIGHT : 0;
        return 16 + rows * ROW_HEIGHT + truncated + 4;
    }

    private static List<Component> chartValues(List<GroupView> rows) {
        return rows.stream()
                .<Component>map(group -> DSKeyLang.DetailLine.getNumber1f(Component.empty(), group.damage(),
                        group.share() * 100, group.hitCount()))
                .toList();
    }

    private boolean insideContent(double mouseX, double mouseY, PageLayout layout) {
        return mouseX >= ScreenLayout.left(width, SIDE) - 2 && mouseX <= ScreenLayout.right(width, SIDE) + 2
                && mouseY >= contentTop(layout) && mouseY < contentBottom(layout);
    }

    private int contentTop(PageLayout layout) {
        return layout.headerHeight() + 6;
    }

    private int contentBottom(PageLayout layout) {
        return layout.footerTop() - 6;
    }

    private PageLayout layout() {
        FooterLayout footer = footerLayout();
        int footerTop = footer.top();
        int maximumHeader = Math.max(0, footerTop - MIN_CONTENT_HEIGHT - 6);
        int titleHeight = Math.min(14, maximumHeader);
        int rowGap = 3;
        int availableRows = Math.max(3, maximumHeader - titleHeight - rowGap * 2 - 4);
        int rowHeight = Math.max(1, Math.min(20, availableRows / 3));
        int top = titleHeight;
        List<ScreenLayout.Bounds> topRow = ScreenLayout.singleRow(width, SIDE, top, rowHeight, 4,
                140, 140, 92, 92);
        int middleTop = top + rowHeight + rowGap;
        List<ScreenLayout.Bounds> middleRow = ScreenLayout.singleRow(width, SIDE, middleTop, rowHeight, 4,
                100, 100, 120, 72, 120);
        int toolbarTop = middleTop + rowHeight + rowGap;
        List<ScreenLayout.Bounds> toolbar = ScreenLayout.singleRow(width, SIDE, toolbarTop, rowHeight, 4,
                92, 68, 84, 58, 68, 68);
        int headerHeight = Math.min(maximumHeader, toolbarTop + rowHeight + 4);
        return new PageLayout(headerHeight, footerTop, topRow, middleRow, toolbar, footer);
    }

    private FooterLayout footerLayout() {
        int[] widths = canResetAll() ? new int[]{86, 86, 84} : new int[]{86, 84};
        ScreenLayout.Flow flow = ScreenLayout.bottomFlow(width, height, SIDE, 20, 4, 4, widths);
        List<ScreenLayout.Bounds> bounds = flow.bounds();
        int footerTop = flow.top();
        if(canResetAll()) return new FooterLayout(footerTop, bounds.get(0), bounds.get(1), bounds.get(2));
        return new FooterLayout(footerTop, bounds.getFirst(), null, bounds.getLast());
    }

    private void appendButtonTooltip(Button button, int mouseX, int mouseY) {
        if(!isHovered(mouseX, mouseY, button.getX(), button.getY(), button.getX() + button.getWidth(),
                button.getY() + button.getHeight())) return;
        if(font.width(button.getMessage()) > Math.max(1, button.getWidth() - 8)) headerTooltip = List.of(button.getMessage());
    }

    private static boolean isHovered(double mouseX, double mouseY, int left, int top, int right, int bottom) {
        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    private Component truncate(Component text, int maxWidth) {
        if(maxWidth <= 0) return Component.empty();
        if(font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int prefixWidth = Math.max(1, maxWidth - font.width(suffix));
        return Component.literal(font.plainSubstrByWidth(text.getString(), prefixWidth) + suffix);
    }

    private static List<Component> chartTooltip(GroupView group) {
        float reduced = Math.max(0, group.originalDamage() - group.damage());
        float rate = group.originalDamage() <= 0 ? 0 : reduced / group.originalDamage() * 100;
        return List.of(
                group.name(),
                DSKeyLang.TotalDamage.getNumber1f(group.damage()),
                DSKeyLang.OriginalDamage.getNumber1f(group.originalDamage()),
                DSKeyLang.ReductionRate.getNumber1f(reduced, rate),
                DSKeyLang.HitCount.get(group.hitCount()),
                DSKeyLang.DetailLine.getNumber1f(Component.empty(), group.damage(), group.share() * 100,
                        group.hitCount()));
    }

    private record FooterLayout(int top, @Nullable ScreenLayout.Bounds reset, @Nullable ScreenLayout.Bounds resetAll,
                                ScreenLayout.Bounds done) {}

    private record PageLayout(int headerHeight, int footerTop,
                              List<ScreenLayout.Bounds> topRow, List<ScreenLayout.Bounds> middleRow,
                              List<ScreenLayout.Bounds> toolbar, FooterLayout footer) {}
}
