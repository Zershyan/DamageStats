package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.client.ClientFocusPreferences;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.*;
import io.zershyan.damagestats.stats.filter.*;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import io.zershyan.damagestats.stats.view.*;
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
    private static final int LARGE_HEADER_HEIGHT = 190;
    private static final int SMALL_HEADER_HEIGHT = 218;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SIDE = 14;
    private static final int LINE_HEIGHT = 12;
    private static final int ROW_HEIGHT = 20;
    private static final int CONTENT_GAP = 8;
    private static final int BACKGROUND = 0xE015181C;
    private static final int HEADER = 0xEE20262C;
    private static final int BORDER = 0xFF505A64;
    private static final int BAR = 0xFF2F8FA3;
    private static final int BAR_HOVER = 0xFF49B4C5;
    private static final int MUTED = 0xFFACB8C2;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int ACCENT = 0xFF55D6E8;

    private FocusChartDimension dimension = FocusChartDimension.DAMAGE_TYPE;
    private FocusChartScope chartScope = FocusChartScope.LIFETIME;
    private DamageTypeGrouping typeGrouping = DamageTypeGrouping.CATEGORY;
    private String chartCursor = "";
    private final List<String> chartCursorHistory = new ArrayList<>();
    private int chartRequestId;
    private int historyRequestId;
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
    private long chartFocusVersion = -1;
    private @Nullable FocusChartPage adopted;
    private @Nullable HistoryPage adoptedHistory;
    private List<Component> chartTooltip = List.of();
    private Button sourceButton;
    private Button targetButton;
    private Button dimensionButton;
    private Button scopeButton;
    private Button groupingButton;
    private Button setFocusButton;
    private Button clearSourceButton;
    private Button clearTargetButton;
    private Button clearChartFiltersButton;
    private Button previousButton;
    private Button nextButton;
    private Button refreshButton;
    private @Nullable Button exportButton;

    public StatsScreen() {
        super(DSKeyLang.ScreenTitle.copy());
    }

    @Override
    protected void init() {
        FocusSummary summary = ClientStats.summary();
        initializeBrowsing(summary);
        chartFocusVersion = summary.scope().version();
        addHeaderButtons();
        addFooterButtons();
        requestFirstChart();
        requestHistory();
        refreshButtons();
    }

    private void addHeaderButtons() {
        int conditionWidth = Math.max(80, (width - SIDE * 2 - 4) / 2);
        sourceButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.SOURCE,
                                browsingFilter(), (selector, name) -> setBrowsingSource(selector, name))))
                .bounds(SIDE, 24, conditionWidth, 20).build());
        targetButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.TARGET,
                                browsingFilter(), (selector, name) -> setBrowsingTarget(selector, name))))
                .bounds(SIDE + conditionWidth + 4, 24, conditionWidth, 20).build());

        setFocusButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenSetFocus.copy(), button ->
                        ClientFocusPreferences.requestFocus(browsingSource, browsingTarget,
                                browsingSourceIsDirectSource))
                .bounds(SIDE, 48, 86, 18).build());
        clearSourceButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearSource.copy(), button -> clearSource())
                .bounds(SIDE + 90, 48, 86, 18).build());
        clearTargetButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearTarget.copy(), button -> clearTarget())
                .bounds(SIDE + 180, 48, 86, 18).build());

        addRenderableWidget(Button.builder(DSKeyLang.ScreenEditOverlay.copy(), button -> openOverlay())
                .bounds(SIDE, 70, 100, 18).build());
        exportButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenExport.copy(), button -> requestExport())
                .bounds(SIDE + 104, 70, 76, 18).build());
        clearChartFiltersButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearChartFilters.copy(),
                        button -> clearChartFilters())
                .bounds(Math.max(SIDE, width - SIDE - 104), 70, 104, 18).build());

        int toolbarY = toolbarY();
        int x = SIDE;
        dimensionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    cycleDimension();
                    requestFirstChart();
                })
                .bounds(x, toolbarY, dimensionWidth(), 18).build());
        x += dimensionWidth() + 4;
        scopeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    chartScope = chartScope == FocusChartScope.SESSION
                            ? FocusChartScope.LIFETIME : FocusChartScope.SESSION;
                    requestFirstChart();
                })
                .bounds(x, toolbarY, scopeWidth(), 18).build());
        x += scopeWidth() + 4;
        groupingButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    typeGrouping = typeGrouping.next();
                    requestFirstChart();
                })
                .bounds(x, toolbarY, groupingWidth(), 18).build());
        x += groupingWidth() + 4;
        refreshButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenRefresh.copy(), button -> requestChart(chartCursor))
                .bounds(x, toolbarY, refreshWidth(), 18).build());

        previousButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenPrevious.copy(), button -> previousChartPage())
                .bounds(compactLayout() ? SIDE : Math.max(SIDE, width - SIDE - 164), paginationY(), 76, 18).build());
        nextButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenNext.copy(), button -> nextChartPage())
                .bounds(compactLayout() ? SIDE + 80 : Math.max(SIDE, width - SIDE - 84), paginationY(), 76, 18).build());
    }

    private void addFooterButtons() {
        int footerY = height - FOOTER_HEIGHT + 6;
        addRenderableWidget(Button.builder(DSKeyLang.ScreenReset.copy(), button -> openResetConfirmation())
                .bounds(SIDE, footerY, 86, 20).build());
        if(canResetAll()) {
            addRenderableWidget(Button.builder(DSKeyLang.ScreenResetAll.copy(), button -> openGlobalResetConfirmation())
                    .bounds(SIDE + 90, footerY, 86, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width - SIDE - 84, footerY, 84, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        FocusSummary summary = ClientStats.summary();
        if(summary.scope().version() != chartFocusVersion) {
            chartFocusVersion = summary.scope().version();
            requestFirstChart();
            requestHistory();
        }
        adoptPages(summary);

        int headerHeight = headerHeight();
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, headerHeight, HEADER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, HEADER);
        graphics.fill(0, headerHeight - 1, width, headerHeight, BORDER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height - FOOTER_HEIGHT + 1, BORDER);
        graphics.drawCenteredString(font, title, width / 2, 7, TEXT);

        initializeBrowsing(summary);
        renderHeader(graphics, summary);
        chartTooltip = List.of();
        renderContent(graphics, summary, adopted, adoptedHistory, mouseX, mouseY);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        if(!chartTooltip.isEmpty()) graphics.renderComponentTooltip(font, chartTooltip, mouseX, mouseY);
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
                contentScrollOffset = 0;
                refreshButtons();
            }
        }
        HistoryPage history = ClientStats.historyPage();
        if(history != null && history != adoptedHistory && history.requestId() == historyRequestId) {
            adoptedHistory = history;
            contentScrollOffset = 0;
        }
    }

    private void renderHeader(GuiGraphics graphics, FocusSummary summary) {
        sourceButton.setMessage(DSKeyLang.FilterSource.get(browsingSourceName));
        targetButton.setMessage(DSKeyLang.FilterTarget.get(browsingTargetName));
        Component state = isCurrentFocus(summary) ? DSKeyLang.ScreenCurrentFocus.copy() : DSKeyLang.ScreenBrowsing.copy();
        graphics.drawString(font, state, width - SIDE - font.width(state), 9, ACCENT);
        renderChartFilters(graphics);
        int metricsY = headerHeight() < LARGE_HEADER_HEIGHT ? 84 : 104;
        boolean compact = headerHeight() < LARGE_HEADER_HEIGHT;
        renderMetrics(graphics, DSKeyLang.SectionSession.copy(), summary.session(), SIDE, metricsY,
                summary.active(), compact);
        renderMetrics(graphics, DSKeyLang.SectionLifetime.copy(), summary.lifetime(), width / 2 + 4,
                metricsY, true, compact);
        refreshButtons();
    }

    private void renderMetrics(GuiGraphics graphics, Component label, FocusMetricsView view, int x, int y,
                               boolean active, boolean compact) {
        MetricsView metrics = view.metrics();
        int color = active ? TEXT : MUTED;
        int columnWidth = Math.max(1, width / 2 - SIDE - 8);
        graphics.drawString(font, label, x, y, ACCENT);
        renderMetricLine(graphics, DSKeyLang.TotalDamage.getNumber1f(metrics.totalActual()), x, y + 12,
                columnWidth, color);
        renderMetricLine(graphics, DSKeyLang.OverlayDps.getNumber1f(metrics.averageDps(), metrics.realtimeDps()),
                x, y + 23, columnWidth, color);
        renderMetricLine(graphics, DSKeyLang.HitCount.get(metrics.hitCount()), x, y + 34, columnWidth, color);
        if(compact) return;
        renderMetricLine(graphics, DSKeyLang.AverageDamage.getNumber1f(metrics.averageDamage()), x, y + 45,
                columnWidth, color);
        renderMetricLine(graphics, DSKeyLang.MaxSingle.getNumber1f(metrics.maxSingle(), metrics.maxSingleTypeName(),
                metrics.maxSingleDirectSourceName(), metrics.maxSingleTime()), x, y + 56, columnWidth, color);
    }

    private void renderMetricLine(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, truncate(text, maxWidth), x, y, color);
    }

    private void renderContent(GuiGraphics graphics, FocusSummary summary, @Nullable FocusChartPage page,
                               @Nullable HistoryPage history, int mouseX, int mouseY) {
        int top = contentTop();
        int bottom = contentBottom();
        if(bottom <= top) return;
        List<ContentLine> lines = contentLines(summary);
        int chartOffset = lines.size() * LINE_HEIGHT + CONTENT_GAP;
        int historyOffset = chartOffset + chartBlockHeight(page) + CONTENT_GAP;
        int totalHeight = historyOffset + historyBlockHeight(history) + CONTENT_GAP;
        int viewportHeight = bottom - top;
        contentScrollOffset = Mth.clamp(contentScrollOffset, 0, Math.max(0, totalHeight - viewportHeight));

        graphics.enableScissor(SIDE - 2, top, width - SIDE + 2, bottom);
        for (int index = 0; index < lines.size(); index++) {
            ContentLine line = lines.get(index);
            int y = top + index * LINE_HEIGHT - contentScrollOffset;
            graphics.drawString(font, truncate(line.text(), Math.max(1, width - SIDE * 2)), SIDE, y, line.color());
        }
        renderChartAt(graphics, page, chartOffset, mouseX, mouseY);
        renderHistory(graphics, history, historyOffset);
        graphics.disableScissor();
    }

    private List<ContentLine> contentLines(FocusSummary summary) {
        List<ContentLine> lines = new ArrayList<>();
        lines.add(new ContentLine(DSKeyLang.FilterSource.get(summary.scope().sourceName()), ACCENT));
        lines.add(new ContentLine(DSKeyLang.FilterTarget.get(summary.scope().targetName()), ACCENT));
        appendMetrics(lines, DSKeyLang.SectionSession.copy(), summary.session(), summary.active());
        appendMetrics(lines, DSKeyLang.SectionLifetime.copy(), summary.lifetime(), true);
        return lines;
    }

    private static void appendMetrics(List<ContentLine> lines, Component heading, FocusMetricsView focusMetrics,
                                      boolean active) {
        MetricsView metrics = focusMetrics.metrics();
        int color = active ? TEXT : MUTED;
        lines.add(new ContentLine(Component.empty(), color));
        lines.add(new ContentLine(heading, ACCENT));
        if(metrics.isEmpty()) {
            lines.add(new ContentLine(DSKeyLang.NoData.copy(), MUTED));
            return;
        }
        lines.add(new ContentLine(DSKeyLang.TotalDamage.getNumber1f(metrics.totalActual()), color));
        lines.add(new ContentLine(DSKeyLang.OriginalDamage.getNumber1f(metrics.totalOriginal()), color));
        lines.add(new ContentLine(DSKeyLang.ReductionRate.getNumber1f(metrics.reducedDamage(),
                metrics.reductionRate() * 100), color));
        lines.add(new ContentLine(DSKeyLang.HitCount.get(metrics.hitCount()), color));
        lines.add(new ContentLine(DSKeyLang.AverageDamage.getNumber1f(metrics.averageDamage()), color));
        lines.add(new ContentLine(DSKeyLang.AverageDps.getNumber1f(metrics.averageDps()), color));
        lines.add(new ContentLine(DSKeyLang.RealtimeDps.getNumber1f(metrics.realtimeDps()), color));
        lines.add(new ContentLine(DSKeyLang.HitsPerSecond.getNumber1f(metrics.hitsPerSecond()), color));
        lines.add(new ContentLine(DSKeyLang.KillCount.get(metrics.killCount()), color));
        lines.add(new ContentLine(DSKeyLang.Duration.getNumber1f(metrics.durationSeconds()), color));
        lines.add(new ContentLine(DSKeyLang.MinSingle.getNumber1f(metrics.minSingle()), color));
        lines.add(new ContentLine(DSKeyLang.MaxSingle.getNumber1f(metrics.maxSingle(), metrics.maxSingleTypeName(),
                metrics.maxSingleDirectSourceName(), metrics.maxSingleTime()), color));
        lines.add(new ContentLine(DSKeyLang.OverlayOriginalAverageDps.getNumber1f(metrics.averageOriginalDps()), color));
        lines.add(new ContentLine(DSKeyLang.OverlayOriginalRealtimeDps.getNumber1f(metrics.realtimeOriginalDps()), color));
        lines.add(new ContentLine(DSKeyLang.OverlayTopDamageType.getNumber1f(focusMetrics.topDamageType().name(),
                focusMetrics.topDamageType().damage(), focusMetrics.topDamageType().share() * 100), color));
        lines.add(new ContentLine(DSKeyLang.OverlayTopDirectSource.getNumber1f(focusMetrics.topDirectSource().name(),
                focusMetrics.topDirectSource().damage(), focusMetrics.topDirectSource().share() * 100), color));
        appendReduction(lines, metrics, color);
    }

    private static void appendReduction(List<ContentLine> lines, MetricsView metrics, int color) {
        lines.add(new ContentLine(DSKeyLang.ReductionArmor.getNumber1f(metrics.reduction().armor()), color));
        lines.add(new ContentLine(DSKeyLang.ReductionEnchantments.getNumber1f(metrics.reduction().enchantments()), color));
        lines.add(new ContentLine(DSKeyLang.ReductionMobEffects.getNumber1f(metrics.reduction().mobEffects()), color));
        lines.add(new ContentLine(DSKeyLang.ReductionAbsorption.getNumber1f(metrics.reduction().absorption()), color));
        lines.add(new ContentLine(DSKeyLang.ReductionInnateResistance.getNumber1f(
                metrics.reduction().innateResistance()), color));
        lines.add(new ContentLine(DSKeyLang.ReductionInvulnerability.getNumber1f(
                metrics.reduction().invulnerability()), color));
    }

    private void renderChartAt(GuiGraphics graphics, @Nullable FocusChartPage page, int contentOffset,
                               int mouseX, int mouseY) {
        int screenTop = contentTop() + contentOffset - contentScrollOffset;
        graphics.drawString(font, dimensionLabel(), SIDE, screenTop, ACCENT);
        int rowTop = screenTop + 16;
        if(!validChartPage(page)) {
            graphics.drawString(font, page != null && !page.allowed() ? DSKeyLang.StatsPrivate.copy()
                    : DSKeyLang.NoData.copy(), SIDE, rowTop, MUTED);
            return;
        }
        List<GroupView> rows = page.rows();
        if(rows.isEmpty()) {
            graphics.drawString(font, DSKeyLang.NoData.copy(), SIDE, rowTop, MUTED);
            return;
        }
        int labelWidth = Math.min(Math.max(80, width / 3), Math.max(1, width - SIDE * 2 - 24));
        int barLeft = SIDE + labelWidth + 8;
        int barWidth = Math.max(1, width - SIDE - barLeft);
        float maxDamage = rows.stream().map(GroupView::damage).max(Float::compare).orElse(1f);
        for (int row = 0; row < rows.size(); row++) {
            GroupView group = rows.get(row);
            int y = rowTop + row * ROW_HEIGHT;
            int widthForDamage = Mth.clamp(Math.round(barWidth * group.damage() / Math.max(1, maxDamage)), 1, barWidth);
            boolean hovered = mouseX >= SIDE && mouseX <= width - SIDE
                    && mouseY >= y && mouseY < y + ROW_HEIGHT - 2
                    && mouseY >= contentTop() && mouseY < contentBottom();
            graphics.fill(barLeft, y + 2, barLeft + widthForDamage, y + ROW_HEIGHT - 3,
                    hovered ? BAR_HOVER : BAR);
            graphics.drawString(font, truncate(group.name(), labelWidth), SIDE, y + 5, TEXT);
            Component value = DSKeyLang.DetailLine.getNumber1f(Component.empty(), group.damage(),
                    group.share() * 100, group.hitCount());
            graphics.drawString(font, truncate(value, Math.max(1, barWidth - 4)), barLeft + 2, y + 5, TEXT);
            if(hovered) chartTooltip = chartTooltip(group);
        }
        if(page.hasNext()) {
            int y = rowTop + rows.size() * ROW_HEIGHT;
            graphics.drawString(font, DSKeyLang.ScreenTruncated.copy(), SIDE, y, MUTED);
        }
    }

    private void renderHistory(GuiGraphics graphics, @Nullable HistoryPage page, int contentOffset) {
        int screenTop = contentTop() + contentOffset - contentScrollOffset;
        graphics.drawString(font, DSKeyLang.SectionHistory.copy(), SIDE, screenTop, ACCENT);
        List<ContentLine> lines = historyLines(page);
        for (int index = 0; index < lines.size(); index++) {
            ContentLine line = lines.get(index);
            int y = screenTop + 16 + index * LINE_HEIGHT;
            graphics.drawString(font, truncate(line.text(), Math.max(1, width - SIDE * 2)), SIDE, y, line.color());
        }
    }

    private static List<ContentLine> historyLines(@Nullable HistoryPage page) {
        if(page == null) return List.of(new ContentLine(DSKeyLang.NoData.copy(), MUTED));
        if(!page.allowed()) return List.of(new ContentLine(DSKeyLang.StatsPrivate.copy(), MUTED));
        if(page.outgoing().isEmpty() && page.incoming().isEmpty()) {
            return List.of(new ContentLine(DSKeyLang.NoData.copy(), MUTED));
        }
        List<ContentLine> lines = new ArrayList<>();
        appendHistory(lines, DSKeyLang.TitleOutgoing.copy(), page.outgoing());
        appendHistory(lines, DSKeyLang.TitleIncoming.copy(), page.incoming());
        return lines;
    }

    private static void appendHistory(List<ContentLine> lines, Component heading, List<SessionView> sessions) {
        if(sessions.isEmpty()) return;
        lines.add(new ContentLine(heading, ACCENT));
        for (int index = 0; index < sessions.size(); index++) {
            SessionView session = sessions.get(index);
            lines.add(new ContentLine(DSKeyLang.SessionLine.getNumber1f(index + 1, session.totalDamage(),
                    session.averageDps(), session.durationSeconds(), session.hitCount()), TEXT));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(mouseY < contentTop() || mouseY >= contentBottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int totalHeight = contentHeight(adopted, adoptedHistory);
        int viewportHeight = Math.max(1, contentBottom() - contentTop());
        int step = Math.max(1, Math.round((float) Math.abs(scrollY) * LINE_HEIGHT));
        if(scrollY > 0) step = -step;
        contentScrollOffset = Mth.clamp(contentScrollOffset + step, 0,
                Math.max(0, totalHeight - viewportHeight));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(super.mouseClicked(mouseX, mouseY, button)) return true;
        if(button != 0 || !insideContent(mouseX, mouseY) || !validChartPage(adopted)) return false;
        int chartOffset = contentLines(ClientStats.summary()).size() * LINE_HEIGHT + CONTENT_GAP;
        int rowTop = contentTop() + chartOffset + 16 - contentScrollOffset;
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
        chartCursorHistory.clear();
        requestChart("");
    }

    private void requestChart(String cursor) {
        chartCursor = cursor == null ? "" : cursor;
        adopted = null;
        contentScrollOffset = 0;
        chartRequestId = ClientStats.nextChartRequestId();
        PacketDistributor.sendToServer(new FocusChartRequestPacket(browsingFilter(), dimension, chartScope,
                typeGrouping, chartCursor, chartRequestId));
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

    private void requestHistory() {
        adoptedHistory = null;
        historyRequestId = ClientStats.nextHistoryRequestId();
        PacketDistributor.sendToServer(new HistoryRequestPacket(browsingFilter(), historyRequestId));
    }

    private void clearSource() {
        if(!canChooseAnySource()) return;
        browsingSource = Optional.empty();
        browsingSourceName = DSKeyLang.ScreenAllDamage.copy();
        browsingSourceIsDirectSource = false;
        normalizeDimension();
        requestFirstChart();
        requestHistory();
    }

    private void clearTarget() {
        browsingTarget = Optional.empty();
        browsingTargetName = DSKeyLang.OverlayAllTargets.copy();
        normalizeDimension();
        requestFirstChart();
        requestHistory();
    }

    private void clearChartFilters() {
        browsingDirectSource = Optional.empty();
        browsingDamageType = Optional.empty();
        browsingDirectSourceName = Component.empty();
        browsingDamageTypeName = Component.empty();
        requestFirstChart();
        requestHistory();
    }

    private void refreshButtons() {
        if(dimensionButton != null) dimensionButton.setMessage(dimensionLabel());
        if(scopeButton != null) scopeButton.setMessage(chartScope == FocusChartScope.SESSION
                ? DSKeyLang.ScopeSession.copy() : DSKeyLang.ScopeLifetime.copy());
        if(groupingButton != null) {
            groupingButton.visible = dimension == FocusChartDimension.DAMAGE_TYPE;
            groupingButton.setMessage(typeGrouping == DamageTypeGrouping.CATEGORY
                    ? DSKeyLang.ScreenGroupingCategory.copy() : DSKeyLang.ScreenGroupingRegistry.copy());
        }
        if(refreshButton != null) refreshButton.setMessage(DSKeyLang.ScreenRefresh.copy());
        if(previousButton != null) previousButton.active = !chartCursorHistory.isEmpty();
        if(nextButton != null) nextButton.active = adopted != null && adopted.hasNext();
        if(sourceButton != null) sourceButton.active = canChooseAnySource();
        if(clearSourceButton != null) clearSourceButton.active = canChooseAnySource() && browsingSource.isPresent();
        if(clearTargetButton != null) clearTargetButton.active = browsingTarget.isPresent();
        if(clearChartFiltersButton != null) {
            clearChartFiltersButton.active = browsingDirectSource.isPresent() || browsingDamageType.isPresent();
        }
        if(setFocusButton != null) {
            var scope = ClientStats.summary().scope();
            setFocusButton.active = !scope.source().equals(browsingSource) || !scope.target().equals(browsingTarget)
                    || scope.sourceIsDirectSource() != browsingSourceIsDirectSource;
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
        normalizeDimension();
        requestFirstChart();
        requestHistory();
    }

    private void setBrowsingTarget(EntitySelector selector, Component name) {
        browsingTarget = Optional.of(selector);
        browsingTargetName = name;
        normalizeDimension();
        requestFirstChart();
        requestHistory();
    }

    private void setBrowsingDirectSource(EntitySelector selector, Component name) {
        browsingSource = Optional.of(selector);
        browsingSourceName = name;
        browsingSourceIsDirectSource = true;
        browsingDirectSource = Optional.empty();
        browsingDirectSourceName = Component.empty();
        normalizeDimension();
        requestFirstChart();
        requestHistory();
    }

    private void applyChartFilter(GroupView group) {
        switch (group.key()) {
            case FilterKey.Source(EntitySelector selector) -> {
                if(group.canOpenInstances()) showEntityActions(group.key(), group.name());
                else setBrowsingSource(selector, group.name());
            }
            case FilterKey.Target(EntitySelector selector) -> {
                if(group.canOpenInstances()) showEntityActions(group.key(), group.name());
                else setBrowsingTarget(selector, group.name());
            }
            case FilterKey.Direct(EntitySelector.Type selector) -> {
                if(group.canOpenInstances()) {
                    showEntityActions(group.key(), group.name());
                    return;
                }
                toggleDirectFilter(selector, group.name());
            }
            case FilterKey.Direct(EntitySelector.Instance selector) -> showEntityActions(group.key(), group.name());
            case FilterKey.Type(DamageTypeSelector selector) -> toggleDamageTypeFilter(selector, group.name());
        }
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
        requestHistory();
    }

    private void toggleDamageTypeFilter(DamageTypeSelector selector, Component name) {
        boolean clearing = browsingDamageType.filter(selector::equals).isPresent();
        browsingDamageType = toggle(browsingDamageType, selector);
        browsingDamageTypeName = clearing ? Component.empty() : name;
        requestFirstChart();
        requestHistory();
    }

    void openInstances(EntitySelector.Type type, FilterKey key) {
        if(key instanceof FilterKey.Direct) {
            minecraft.setScreen(FocusEntitySelectorScreen.directSourceInstances(this, type.typeId(), browsingFilter(),
                    (selector, name) -> showEntityActions(new FilterKey.Direct(selector), name)));
            return;
        }
        FocusSelectionSlot slot = key instanceof FilterKey.Source ? FocusSelectionSlot.SOURCE : FocusSelectionSlot.TARGET;
        minecraft.setScreen(FocusEntitySelectorScreen.instances(this, slot, type.typeId(), browsingFilter(),
                (selector, name) -> showEntityActions(
                        slot == FocusSelectionSlot.SOURCE ? new FilterKey.Source(selector) : new FilterKey.Target(selector),
                        name)));
    }

    boolean canChooseAnySource() {
        return minecraft.player != null && minecraft.player.hasPermissions(2);
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
        return minecraft.player != null && minecraft.player.hasPermissions(2);
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

    private void renderChartFilters(GuiGraphics graphics) {
        MutableComponent line = Component.empty();
        if(browsingDirectSource.isPresent()) line.append(DSKeyLang.FilterDirect.get(browsingDirectSourceName));
        if(browsingDamageType.isPresent()) {
            if(!line.getString().isEmpty()) line.append(Component.literal("  "));
            line.append(DSKeyLang.FilterType.get(browsingDamageTypeName));
        }
        if(line.getString().isEmpty()) return;
        graphics.drawString(font, truncate(line, Math.max(1, width - SIDE * 2 - 110)), SIDE, 91, MUTED);
    }

    private void cycleDimension() {
        FocusChartDimension[] values = FocusChartDimension.values();
        for (int offset = 1; offset <= values.length; offset++) {
            FocusChartDimension candidate = values[(dimension.ordinal() + offset) % values.length];
            if(supportsDimension(candidate)) {
                dimension = candidate;
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

    private int contentHeight(@Nullable FocusChartPage page, @Nullable HistoryPage history) {
        List<ContentLine> lines = contentLines(ClientStats.summary());
        return lines.size() * LINE_HEIGHT + CONTENT_GAP + chartBlockHeight(page) + CONTENT_GAP
                + historyBlockHeight(history) + CONTENT_GAP;
    }

    private int chartBlockHeight(@Nullable FocusChartPage page) {
        int rows = validChartPage(page) ? Math.max(1, page.rows().size()) : 1;
        int truncated = validChartPage(page) && page.hasNext() ? LINE_HEIGHT : 0;
        return 16 + rows * ROW_HEIGHT + truncated + 4;
    }

    private static int historyBlockHeight(@Nullable HistoryPage page) {
        return 16 + historyLines(page).size() * LINE_HEIGHT + 4;
    }

    private boolean insideContent(double mouseX, double mouseY) {
        return mouseX >= SIDE - 2 && mouseX <= width - SIDE + 2
                && mouseY >= contentTop() && mouseY < contentBottom();
    }

    private int contentTop() {
        return headerHeight() + 6;
    }

    private int contentBottom() {
        return height - FOOTER_HEIGHT - 6;
    }

    private int headerHeight() {
        int preferred = compactLayout() ? SMALL_HEADER_HEIGHT : LARGE_HEADER_HEIGHT;
        int available = Math.max(128, height - FOOTER_HEIGHT - 24);
        return Math.min(preferred, available);
    }

    private boolean compactLayout() {
        return width < 720 || height < 480;
    }

    private int toolbarY() {
        if(!compactLayout()) return headerHeight() - 22;
        return headerHeight() < LARGE_HEADER_HEIGHT ? headerHeight() - 22 : headerHeight() - 44;
    }

    private int paginationY() {
        if(!compactLayout()) return toolbarY();
        return headerHeight() < LARGE_HEADER_HEIGHT ? headerHeight() - 44 : headerHeight() - 22;
    }

    private int dimensionWidth() {
        return compactLayout() ? 80 : 96;
    }

    private int scopeWidth() {
        return compactLayout() ? 56 : 68;
    }

    private int groupingWidth() {
        return compactLayout() ? 72 : 88;
    }

    private int refreshWidth() {
        return compactLayout() ? 48 : 58;
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

    private record ContentLine(Component text, int color) {}
}
