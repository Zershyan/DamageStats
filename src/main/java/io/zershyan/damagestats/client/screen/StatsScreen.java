package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.client.ClientFocusPreferences;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.ExportRequestPacket;
import io.zershyan.damagestats.registry.packet.FocusChartRequestPacket;
import io.zershyan.damagestats.registry.packet.FocusGuiClosedPacket;
import io.zershyan.damagestats.registry.packet.ResetStatsPacket;
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

import java.util.List;
import java.util.Optional;

/** 全屏焦点仪表盘：页头即时更新，页间图表仅通过分页请求按需更新。 */
public class StatsScreen extends Screen {
    private static final int HEADER_HEIGHT = 164;
    private static final int FOOTER_HEIGHT = 32;
    private static final int SIDE = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int BACKGROUND = 0xE015181C;
    private static final int HEADER = 0xEE20262C;
    private static final int BORDER = 0xFF505A64;
    private static final int BAR = 0xFF2F8FA3;
    private static final int BAR_HOVER = 0xFF49B4C5;
    private static final int MUTED = 0xFFACB8C2;
    private static final int TEXT = 0xFFFFFFFF;

    private FocusChartDimension dimension = FocusChartDimension.DAMAGE_TYPE;
    private FocusChartScope chartScope = FocusChartScope.LIFETIME;
    private DamageTypeGrouping typeGrouping = DamageTypeGrouping.CATEGORY;
    private int chartCursor;
    private int chartRequestId;
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
    private int rowOffset;
    private @Nullable FocusChartPage adopted;
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
        initializeBrowsing(ClientStats.summary());
        int headerButtonY = 24;
        int conditionWidth = Math.max(80, (width - SIDE * 2 - 4) / 2);
        sourceButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.SOURCE,
                                browsingFilter(),
                                (selector, name) -> setBrowsingSource(selector, name))))
                .bounds(SIDE, headerButtonY, conditionWidth, 20).build());
        targetButton = addRenderableWidget(Button.builder(Component.empty(), button ->
                        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.TARGET,
                                browsingFilter(),
                                (selector, name) -> setBrowsingTarget(selector, name))))
                .bounds(SIDE + conditionWidth + 4, headerButtonY, conditionWidth, 20).build());
        setFocusButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenSetFocus.copy(), button ->
                        ClientFocusPreferences.requestFocus(browsingSource, browsingTarget,
                                browsingSourceIsDirectSource))
                .bounds(width / 2 - 138, 47, 90, 18).build());
        clearSourceButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearSource.copy(), button -> {
                    browsingSource = Optional.empty();
                    browsingSourceName = DSKeyLang.ScreenAllDamage.copy();
                    browsingSourceIsDirectSource = false;
                    requestChart(0);
                })
                .bounds(width / 2 - 45, 47, 90, 18).build());
        clearTargetButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearTarget.copy(), button -> {
                    browsingTarget = Optional.empty();
                    browsingTargetName = DSKeyLang.OverlayAllTargets.copy();
                    requestChart(0);
                })
                .bounds(width / 2 + 48, 47, 90, 18).build());
        clearChartFiltersButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenClearChartFilters.copy(), button -> {
                    browsingDirectSource = Optional.empty();
                    browsingDamageType = Optional.empty();
                    browsingDirectSourceName = Component.empty();
                    browsingDamageTypeName = Component.empty();
                    requestChart(0);
                })
                .bounds(width - SIDE - 104, 67, 104, 18).build());
        int toolbarY = HEADER_HEIGHT - 24;
        dimensionButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    dimension = FocusChartDimension.values()[(dimension.ordinal() + 1) % FocusChartDimension.values().length];
                    requestChart(0);
                })
                .bounds(SIDE, toolbarY, 90, 18).build());
        scopeButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    chartScope = chartScope == FocusChartScope.SESSION ? FocusChartScope.LIFETIME : FocusChartScope.SESSION;
                    requestChart(0);
                })
                .bounds(SIDE + 94, toolbarY, 60, 18).build());
        groupingButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
                    typeGrouping = typeGrouping.next();
                    requestChart(0);
                })
                .bounds(SIDE + 158, toolbarY, 80, 18).build());
        refreshButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenRefresh.copy(), button -> requestChart(chartCursor))
                .bounds(SIDE + 242, toolbarY, 42, 18).build());
        int footerY = height - FOOTER_HEIGHT + 6;
        if(width < 880) {
            addRenderableWidget(Button.builder(DSKeyLang.ScreenActions.copy(), button -> minecraft.setScreen(new StatsActionsScreen(this)))
                    .bounds(SIDE, footerY, 74, 20).build());
            previousButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenPrevious.copy(),
                            button -> requestChart(chartCursor - FocusChartPage.PAGE_SIZE))
                    .bounds(width - SIDE - 190, footerY, 56, 20).build());
            nextButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenNext.copy(),
                            button -> requestChart(chartCursor + FocusChartPage.PAGE_SIZE))
                    .bounds(width - SIDE - 130, footerY, 56, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(width - SIDE - 70, footerY, 70, 20).build());
        } else {
            addRenderableWidget(Button.builder(DSKeyLang.ScreenReset.copy(), button -> openResetConfirmation())
                    .bounds(SIDE, footerY, 86, 20).build());
            addRenderableWidget(Button.builder(DSKeyLang.ScreenEditOverlay.copy(), button -> openOverlay())
                    .bounds(SIDE + 90, footerY, 100, 20).build());
            addRenderableWidget(Button.builder(DSKeyLang.ScreenDetails.copy(), button -> openDetails())
                    .bounds(SIDE + 194, footerY, 80, 20).build());
            addRenderableWidget(Button.builder(DSKeyLang.ScreenHistory.copy(), button -> openHistory())
                    .bounds(SIDE + 278, footerY, 80, 20).build());
            exportButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenExport.copy(), button -> requestExport())
                    .bounds(SIDE + 362, footerY, 72, 20).build());
            if(canResetAll()) {
                addRenderableWidget(Button.builder(DSKeyLang.ScreenResetAll.copy(), button -> openGlobalResetConfirmation())
                        .bounds(SIDE + 438, footerY, 86, 20).build());
            }
            previousButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenPrevious.copy(),
                            button -> requestChart(chartCursor - FocusChartPage.PAGE_SIZE))
                    .bounds(width - SIDE - 248, footerY, 76, 20).build());
            nextButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenNext.copy(),
                            button -> requestChart(chartCursor + FocusChartPage.PAGE_SIZE))
                    .bounds(width - SIDE - 168, footerY, 76, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                    .bounds(width - SIDE - 84, footerY, 84, 20).build());
        }
        requestChart(0);
        refreshButtons();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        FocusSummary summary = ClientStats.summary();
        FocusChartPage page = ClientStats.chartPage();
        if(page != null && page != adopted && page.requestId() == chartRequestId
                && page.focusVersion() == summary.scope().version()) {
            adopted = page;
            chartCursor = page.rows().isEmpty() && chartCursor > 0 ? 0 : chartCursor;
            rowOffset = 0;
            refreshButtons();
        }
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, HEADER_HEIGHT, HEADER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, HEADER);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, BORDER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height - FOOTER_HEIGHT + 1, BORDER);
        graphics.drawCenteredString(font, title, width / 2, 8, TEXT);
        initializeBrowsing(summary);
        renderHeader(graphics, summary);
        chartTooltip = List.of();
        renderChart(graphics, page, mouseX, mouseY);
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        if(!chartTooltip.isEmpty()) graphics.renderComponentTooltip(font, chartTooltip, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics graphics, FocusSummary summary) {
        sourceButton.setMessage(DSKeyLang.FilterSource.get(browsingSourceName));
        targetButton.setMessage(DSKeyLang.FilterTarget.get(browsingTargetName));
        int firstColumn = SIDE;
        int secondColumn = width / 2 + 8;
        Component state = isCurrentFocus(summary) ? DSKeyLang.ScreenCurrentFocus.copy() : DSKeyLang.ScreenBrowsing.copy();
        graphics.drawString(font, state, width - SIDE - font.width(state), 10, 0xFF55D6E8);
        renderChartFilters(graphics);
        renderMetrics(graphics, DSKeyLang.SectionSession.copy(), summary.session(), firstColumn, 88, summary.active());
        renderMetrics(graphics, DSKeyLang.SectionLifetime.copy(), summary.lifetime(), secondColumn, 88, true);
        refreshButtons();
    }

    private void renderMetrics(GuiGraphics graphics, Component label, FocusMetricsView view, int x, int y, boolean active) {
        MetricsView metrics = view.metrics();
        int color = active ? TEXT : MUTED;
        int columnWidth = width / 2 - SIDE - 12;
        graphics.drawString(font, label, x, y, 0xFF55D6E8);
        renderMetricLine(graphics, DSKeyLang.TotalDamage.getNumber1f(metrics.totalActual()), x, y + 12, columnWidth, color);
        renderMetricLine(graphics, DSKeyLang.OverlayDps.getNumber1f(metrics.averageDps(), metrics.realtimeDps()), x, y + 23, columnWidth, color);
        renderMetricLine(graphics, DSKeyLang.HitCount.get(metrics.hitCount()), x, y + 34, columnWidth, color);
        renderMetricLine(graphics, DSKeyLang.AverageDamage.getNumber1f(metrics.averageDamage()), x, y + 45, columnWidth, color);
        renderMetricLine(graphics, DSKeyLang.OverlayHighestFinal.getNumber1f(metrics.maxSingle()), x, y + 56, columnWidth, color);
    }

    private void renderMetricLine(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text.getString(), Math.max(1, maxWidth)), x, y, color);
    }

    private void renderChart(GuiGraphics graphics, @Nullable FocusChartPage page, int mouseX, int mouseY) {
        int top = HEADER_HEIGHT + 8;
        int bottom = height - FOOTER_HEIGHT - 6;
        if(page == null || page.requestId() != chartRequestId
                || page.focusVersion() != ClientStats.summary().scope().version() || !page.allowed()) {
            graphics.drawCenteredString(font, DSKeyLang.NoData.copy(), width / 2, top + 8, MUTED);
            return;
        }
        List<GroupView> rows = page.rows();
        if(rows.isEmpty()) {
            graphics.drawCenteredString(font, DSKeyLang.NoData.copy(), width / 2, top + 8, MUTED);
            return;
        }
        int visibleRows = Math.max(1, (bottom - top) / ROW_HEIGHT);
        int count = Math.min(visibleRows, rows.size() - rowOffset);
        int labelWidth = Math.min(Math.max(140, width / 3), width - SIDE * 2 - 80);
        int barLeft = SIDE + labelWidth + 8;
        int barWidth = Math.max(1, width - SIDE - barLeft);
        for (int row = 0; row < count; row++) {
            GroupView group = rows.get(rowOffset + row);
            int y = top + row * ROW_HEIGHT;
            int widthForDamage = Mth.clamp(Math.round(barWidth * group.share()), 1, barWidth);
            boolean hovered = mouseX >= SIDE && mouseX <= width - SIDE && mouseY >= y && mouseY < y + ROW_HEIGHT - 2;
            graphics.fill(barLeft, y + 2, barLeft + widthForDamage, y + ROW_HEIGHT - 3, hovered ? BAR_HOVER : BAR);
            graphics.drawString(font, font.plainSubstrByWidth(group.name().getString(), labelWidth), SIDE, y + 5, TEXT);
            Component value = DSKeyLang.DetailLine.getNumber1f(Component.empty(), group.damage(), group.share() * 100, group.hitCount());
            graphics.drawString(font, font.plainSubstrByWidth(value.getString(), widthForDamage - 4),
                    barLeft + 2, y + 5, TEXT);
            if(hovered) chartTooltip = chartTooltip(group);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(adopted == null || mouseY < HEADER_HEIGHT || mouseY > height - FOOTER_HEIGHT) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int visibleRows = Math.max(1, (height - FOOTER_HEIGHT - 6 - (HEADER_HEIGHT + 8)) / ROW_HEIGHT);
        rowOffset = Mth.clamp(rowOffset - (int) Math.signum(scrollY), 0, Math.max(0, adopted.rows().size() - visibleRows));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(super.mouseClicked(mouseX, mouseY, button)) return true;
        if(button != 0 || adopted == null || mouseY < HEADER_HEIGHT || mouseY >= height - FOOTER_HEIGHT) return false;
        int row = (int) ((mouseY - HEADER_HEIGHT - 8) / ROW_HEIGHT) + rowOffset;
        if(row < 0 || row >= adopted.rows().size()) return false;
        applyChartFilter(adopted.rows().get(row));
        return true;
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(FocusGuiClosedPacket.INSTANCE);
        super.onClose();
    }

    private void requestChart(int cursor) {
        chartCursor = Math.max(0, cursor);
        adopted = null;
        rowOffset = 0;
        chartRequestId = ClientStats.nextChartRequestId();
        PacketDistributor.sendToServer(new FocusChartRequestPacket(browsingFilter(), dimension, chartScope,
                typeGrouping, chartCursor, chartRequestId));
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
        if(previousButton != null) previousButton.active = chartCursor > 0;
        if(nextButton != null) nextButton.active = adopted != null && adopted.hasNext();
        if(sourceButton != null) sourceButton.active = minecraft.player != null && minecraft.player.hasPermissions(2);
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

    private StatsFilter browsingFilter() {
        return new StatsFilter(browsingSource, browsingTarget, browsingDirectSource, browsingDamageType,
                browsingSourceIsDirectSource);
    }

    private void setBrowsingSource(EntitySelector selector, Component name) {
        browsingSource = Optional.of(selector);
        browsingSourceName = name;
        browsingSourceIsDirectSource = false;
        requestChart(0);
    }

    private void setBrowsingTarget(EntitySelector selector, Component name) {
        browsingTarget = Optional.of(selector);
        browsingTargetName = name;
        requestChart(0);
    }

    private void setBrowsingDirectSource(EntitySelector selector, Component name) {
        browsingSource = Optional.of(selector);
        browsingSourceName = name;
        browsingSourceIsDirectSource = true;
        browsingDirectSource = Optional.empty();
        browsingDirectSourceName = Component.empty();
        requestChart(0);
    }

    private void applyChartFilter(GroupView group) {
        switch (group.key()) {
            case FilterKey.Source(EntitySelector selector) -> {
                if(group.canOpenInstances()) showEntityActions(selector, group.name(), false);
                else setBrowsingSource(selector, group.name());
            }
            case FilterKey.Target(EntitySelector selector) -> {
                if(group.canOpenInstances()) showEntityActions(selector, group.name(), false);
                else setBrowsingTarget(selector, group.name());
            }
            case FilterKey.Direct(EntitySelector.Type selector) -> {
                if(group.canOpenInstances()) {
                    showEntityActions(selector, group.name(), true);
                    return;
                }
                toggleDirectFilter(selector, group.name());
                return;
            }
            case FilterKey.Direct(EntitySelector.Instance selector) -> {
                toggleDirectFilter(selector, group.name());
                return;
            }
            case FilterKey.Type(DamageTypeSelector selector) -> {
                toggleDamageTypeFilter(selector, group.name());
                return;
            }
        }
    }

    private static <T> Optional<T> toggle(Optional<T> current, T selected) {
        return current.filter(selected::equals).isPresent() ? Optional.empty() : Optional.of(selected);
    }

    void showEntityActions(EntitySelector selector, Component name, boolean directSource) {
        minecraft.setScreen(new ChartEntityActionScreen(this, selector, name, directSource));
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
        requestChart(0);
    }

    private void toggleDamageTypeFilter(DamageTypeSelector selector, Component name) {
        boolean clearing = browsingDamageType.filter(selector::equals).isPresent();
        browsingDamageType = toggle(browsingDamageType, selector);
        browsingDamageTypeName = clearing ? Component.empty() : name;
        requestChart(0);
    }

    void openInstances(EntitySelector.Type type, boolean directSource) {
        if(directSource) {
            minecraft.setScreen(FocusEntitySelectorScreen.directSourceInstances(this, type.typeId(), browsingFilter(),
                    (selector, name) -> showEntityActions(selector, name, true)));
            return;
        }
        minecraft.setScreen(FocusEntitySelectorScreen.instances(this, FocusSelectionSlot.TARGET, type.typeId(), browsingFilter(),
                (selector, name) -> showEntityActions(selector, name, false)));
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
        PacketDistributor.sendToServer(new ExportRequestPacket(browsingFilter(), requestId));
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
        int maxWidth = width - SIDE * 2 - 110;
        graphics.drawString(font, font.plainSubstrByWidth(line.getString(), Math.max(1, maxWidth)), SIDE, 72, MUTED);
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
                DSKeyLang.DetailLine.getNumber1f(Component.empty(), group.damage(), group.share() * 100, group.hitCount()));
    }
}
