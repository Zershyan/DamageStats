package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.DamageReduction;
import io.zershyan.damagestats.stats.view.FocusMetricsView;
import io.zershyan.damagestats.stats.view.FocusSummary;
import io.zershyan.damagestats.stats.view.MetricsView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 焦点详细指标页直接读取最新摘要，避免为只读指标再发送全量数据。 */
public final class StatsDetailsScreen extends Screen {
    private static final int SIDE = 18;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 32;
    private static final int LINE_HEIGHT = 12;
    private static final int BACKGROUND = 0xE015181C;
    private static final int HEADER = 0xEE20262C;
    private static final int BORDER = 0xFF505A64;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFACB8C2;
    private static final int ACCENT = 0xFF55D6E8;

    private final StatsScreen parent;
    private int scrollOffset;

    public StatsDetailsScreen(StatsScreen parent) {
        super(DSKeyLang.ScreenDetails.copy());
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> minecraft.setScreen(parent))
                .bounds(width - SIDE - 84, height - FOOTER_HEIGHT + 6, 84, 20).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, HEADER_HEIGHT, HEADER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height, HEADER);
        graphics.fill(0, HEADER_HEIGHT - 1, width, HEADER_HEIGHT, BORDER);
        graphics.fill(0, height - FOOTER_HEIGHT, width, height - FOOTER_HEIGHT + 1, BORDER);
        graphics.drawCenteredString(font, title, width / 2, 11, TEXT);

        List<DetailLine> lines = detailLines(ClientStats.summary());
        int top = HEADER_HEIGHT + 6;
        int bottom = height - FOOTER_HEIGHT - 6;
        int visible = Math.max(1, (bottom - top) / LINE_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, lines.size() - visible));
        for (int index = scrollOffset; index < Math.min(lines.size(), scrollOffset + visible); index++) {
            DetailLine line = lines.get(index);
            int y = top + (index - scrollOffset) * LINE_HEIGHT;
            graphics.drawString(font, font.plainSubstrByWidth(line.text().getString(), Math.max(1, width - SIDE * 2)),
                    SIDE, y, line.color());
        }
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(mouseY < HEADER_HEIGHT || mouseY >= height - FOOTER_HEIGHT) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int visible = Math.max(1, (height - FOOTER_HEIGHT - 6 - (HEADER_HEIGHT + 6)) / LINE_HEIGHT);
        int maxOffset = Math.max(0, detailLines(ClientStats.summary()).size() - visible);
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxOffset);
        return true;
    }

    private static List<DetailLine> detailLines(FocusSummary summary) {
        List<DetailLine> lines = new ArrayList<>();
        lines.add(new DetailLine(DSKeyLang.FilterSource.get(summary.scope().sourceName()), ACCENT));
        lines.add(new DetailLine(DSKeyLang.FilterTarget.get(summary.scope().targetName()), ACCENT));
        appendMetrics(lines, DSKeyLang.SectionSession.copy(), summary.session(), summary.active());
        appendMetrics(lines, DSKeyLang.SectionLifetime.copy(), summary.lifetime(), true);
        return lines;
    }

    private static void appendMetrics(List<DetailLine> lines, Component heading, FocusMetricsView focusMetrics, boolean active) {
        MetricsView metrics = focusMetrics.metrics();
        int color = active ? TEXT : MUTED;
        lines.add(new DetailLine(Component.empty(), color));
        lines.add(new DetailLine(heading, ACCENT));
        if(metrics.isEmpty()) {
            lines.add(new DetailLine(DSKeyLang.NoData.copy(), MUTED));
            return;
        }
        lines.add(new DetailLine(DSKeyLang.TotalDamage.getNumber1f(metrics.totalActual()), color));
        lines.add(new DetailLine(DSKeyLang.OriginalDamage.getNumber1f(metrics.totalOriginal()), color));
        lines.add(new DetailLine(DSKeyLang.ReductionRate.getNumber1f(metrics.reducedDamage(), metrics.reductionRate() * 100), color));
        lines.add(new DetailLine(DSKeyLang.HitCount.get(metrics.hitCount()), color));
        lines.add(new DetailLine(DSKeyLang.AverageDamage.getNumber1f(metrics.averageDamage()), color));
        lines.add(new DetailLine(DSKeyLang.AverageDps.getNumber1f(metrics.averageDps()), color));
        lines.add(new DetailLine(DSKeyLang.RealtimeDps.getNumber1f(metrics.realtimeDps()), color));
        lines.add(new DetailLine(DSKeyLang.HitsPerSecond.getNumber1f(metrics.hitsPerSecond()), color));
        lines.add(new DetailLine(DSKeyLang.KillCount.get(metrics.killCount()), color));
        lines.add(new DetailLine(DSKeyLang.Duration.getNumber1f(metrics.durationSeconds()), color));
        lines.add(new DetailLine(DSKeyLang.MinSingle.getNumber1f(metrics.minSingle()), color));
        lines.add(new DetailLine(DSKeyLang.MaxSingle.getNumber1f(metrics.maxSingle(), metrics.maxSingleTypeName(),
                metrics.maxSingleDirectSourceName(), metrics.maxSingleTime()), color));
        lines.add(new DetailLine(DSKeyLang.OverlayOriginalAverageDps.getNumber1f(metrics.averageOriginalDps()), color));
        lines.add(new DetailLine(DSKeyLang.OverlayOriginalRealtimeDps.getNumber1f(metrics.realtimeOriginalDps()), color));
        appendContribution(lines, DSKeyLang.OverlayTopDamageType.getNumber1f(focusMetrics.topDamageType().name(),
                focusMetrics.topDamageType().damage(), focusMetrics.topDamageType().share() * 100), color);
        appendContribution(lines, DSKeyLang.OverlayTopDirectSource.getNumber1f(focusMetrics.topDirectSource().name(),
                focusMetrics.topDirectSource().damage(), focusMetrics.topDirectSource().share() * 100), color);
        appendReduction(lines, metrics.reduction(), color);
    }

    private static void appendContribution(List<DetailLine> lines, Component contribution, int color) {
        lines.add(new DetailLine(contribution, color));
    }

    private static void appendReduction(List<DetailLine> lines, DamageReduction reduction, int color) {
        lines.add(new DetailLine(DSKeyLang.ReductionArmor.getNumber1f(reduction.armor()), color));
        lines.add(new DetailLine(DSKeyLang.ReductionEnchantments.getNumber1f(reduction.enchantments()), color));
        lines.add(new DetailLine(DSKeyLang.ReductionMobEffects.getNumber1f(reduction.mobEffects()), color));
        lines.add(new DetailLine(DSKeyLang.ReductionAbsorption.getNumber1f(reduction.absorption()), color));
        lines.add(new DetailLine(DSKeyLang.ReductionInnateResistance.getNumber1f(reduction.innateResistance()), color));
        lines.add(new DetailLine(DSKeyLang.ReductionInvulnerability.getNumber1f(reduction.invulnerability()), color));
    }

    private record DetailLine(Component text, int color) {}
}
