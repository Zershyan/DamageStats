package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.ui.UiButton;
import io.zershyan.damagestats.client.ui.UiTheme;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.DamageReduction;
import io.zershyan.damagestats.stats.view.FocusMetricsView;
import io.zershyan.damagestats.stats.view.FocusSummary;
import io.zershyan.damagestats.stats.view.MetricsView;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** 焦点详细指标页直接读取最新摘要，避免为只读指标再发送全量数据。 */
public final class StatsDetailsScreen extends ListScreen {
    private final StatsScreen parent;
    private long cachedSummaryRevision = -1;
    private long cachedScopeVersion = -1;
    private List<Line> cachedLines = List.of();

    public StatsDetailsScreen(StatsScreen parent) {
        super(DSKeyLang.ScreenDetails.copy());
        this.parent = parent;
    }

    @Override
    protected List<Line> buildLines() {
        FocusSummary summary = ClientStats.summary();
        if(summary.revision() == cachedSummaryRevision && summary.scope().version() == cachedScopeVersion) {
            return cachedLines;
        }
        cachedSummaryRevision = summary.revision();
        cachedScopeVersion = summary.scope().version();
        cachedLines = build(summary);
        return cachedLines;
    }

    @Override
    protected List<UiButton> buildFooterActions() {
        return List.of(UiButton.builder(CommonComponents.GUI_DONE, button -> minecraft.setScreen(parent))
                .bounds(footerSlot(0, 1, 92)).variant(UiButton.Variant.SECONDARY).build());
    }

    private static List<Line> build(FocusSummary summary) {
        List<Line> lines = new ArrayList<>();
        lines.add(Line.heading(DSKeyLang.FilterSource.get(summary.scope().sourceName())));
        lines.add(Line.heading(DSKeyLang.FilterTarget.get(summary.scope().targetName())));
        appendMetrics(lines, DSKeyLang.SectionSession.copy(), summary.session(), summary.active());
        appendMetrics(lines, DSKeyLang.SectionLifetime.copy(), summary.lifetime(), true);
        return lines;
    }

    private static void appendMetrics(List<Line> lines, Component heading, FocusMetricsView focusMetrics,
                                      boolean active) {
        MetricsView metrics = focusMetrics.metrics();
        lines.add(Line.spacer());
        lines.add(Line.heading(heading));
        if(metrics.isEmpty()) {
            lines.add(Line.muted(DSKeyLang.NoData.copy()));
            return;
        }
        int color = active ? UiTheme.TEXT : UiTheme.TEXT_MUTED;
        lines.add(Line.of(DSKeyLang.TotalDamage.getNumber1f(metrics.totalActual()), color));
        lines.add(Line.of(DSKeyLang.OriginalDamage.getNumber1f(metrics.totalOriginal()), color));
        lines.add(Line.of(DSKeyLang.ReductionRate.getNumber1f(metrics.reducedDamage(),
                metrics.reductionRate() * 100), color));
        lines.add(Line.of(DSKeyLang.HitCount.get(metrics.hitCount()), color));
        lines.add(Line.of(DSKeyLang.AverageDamage.getNumber1f(metrics.averageDamage()), color));
        lines.add(Line.of(DSKeyLang.AverageDps.getNumber1f(metrics.averageDps()), color));
        lines.add(Line.of(DSKeyLang.RealtimeDps.getNumber1f(metrics.realtimeDps()), color));
        lines.add(Line.of(DSKeyLang.HitsPerSecond.getNumber1f(metrics.hitsPerSecond()), color));
        lines.add(Line.of(DSKeyLang.KillCount.get(metrics.killCount()), color));
        lines.add(Line.of(DSKeyLang.Duration.getNumber1f(metrics.durationSeconds()), color));
        lines.add(Line.of(DSKeyLang.MinSingle.getNumber1f(metrics.minSingle()), color));
        lines.add(Line.of(DSKeyLang.MaxSingle.getNumber1f(metrics.maxSingle(), metrics.maxSingleTypeName(),
                metrics.maxSingleDirectSourceName(), metrics.maxSingleTime()), color));
        lines.add(Line.of(DSKeyLang.OverlayOriginalAverageDps.getNumber1f(metrics.averageOriginalDps()), color));
        lines.add(Line.of(DSKeyLang.OverlayOriginalRealtimeDps.getNumber1f(metrics.realtimeOriginalDps()), color));
        lines.add(Line.of(DSKeyLang.OverlayTopDamageType.getNumber1f(focusMetrics.topDamageType().name(),
                focusMetrics.topDamageType().damage(), focusMetrics.topDamageType().share() * 100), color));
        lines.add(Line.of(DSKeyLang.OverlayTopDirectSource.getNumber1f(focusMetrics.topDirectSource().name(),
                focusMetrics.topDirectSource().damage(), focusMetrics.topDirectSource().share() * 100), color));
        appendReduction(lines, metrics.reduction(), color);
    }

    private static void appendReduction(List<Line> lines, DamageReduction reduction, int color) {
        lines.add(Line.of(DSKeyLang.ReductionArmor.getNumber1f(reduction.armor()), color));
        lines.add(Line.of(DSKeyLang.ReductionEnchantments.getNumber1f(reduction.enchantments()), color));
        lines.add(Line.of(DSKeyLang.ReductionMobEffects.getNumber1f(reduction.mobEffects()), color));
        lines.add(Line.of(DSKeyLang.ReductionAbsorption.getNumber1f(reduction.absorption()), color));
        lines.add(Line.of(DSKeyLang.ReductionInnateResistance.getNumber1f(reduction.innateResistance()), color));
        lines.add(Line.of(DSKeyLang.ReductionInvulnerability.getNumber1f(reduction.invulnerability()), color));
    }
}
