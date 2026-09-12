package io.zershyan.damagestats.client.overlay;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.config.OverlayMetricScope;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.DamageReduction;
import io.zershyan.damagestats.stats.focus.FocusScopeView;
import io.zershyan.damagestats.stats.view.ContributionView;
import io.zershyan.damagestats.stats.view.FocusMetricsView;
import io.zershyan.damagestats.stats.view.FocusSummary;
import io.zershyan.damagestats.stats.view.MetricsView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 按客户端字段偏好绘制服务端焦点摘要，不保存或推断统计数据。 */
public final class StatsOverlay implements LayeredDraw.Layer {
    private static final int MIN_WIDTH = 112;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 4;
    private static final int TITLE_COLOR = 0xFFFFD700;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_COLOR = 0xFF9E9E9E;

    private record Line(Component text, int color) {}

    public static int width() {
        Minecraft minecraft = Minecraft.getInstance();
        return Math.min(scaled(measureWidth(minecraft.font, previewSummary())), minecraft.getWindow().getGuiScaledWidth());
    }

    public static int height() {
        Minecraft minecraft = Minecraft.getInstance();
        return Math.min(scaled(measureHeight(previewSummary())), minecraft.getWindow().getGuiScaledHeight());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, @NotNull DeltaTracker deltaTracker) {
        if(!DSClientConfig.OverlayVisible.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.options.hideGui) return;
        if(minecraft.screen != null && !(minecraft.screen instanceof ChatScreen)) return;
        FocusSummary summary = ClientStats.summary();
        if(summary.lifetime().metrics().hitCount() == 0) return;
        renderBox(graphics, minecraft.font, summary,
                originX(graphics.guiWidth()), originY(graphics.guiHeight()));
    }

    public static int originX(int screenWidth) {
        return clampOrigin((int) (screenWidth * DSClientConfig.OverlayX.get()), width(), screenWidth);
    }

    public static int originY(int screenHeight) {
        return clampOrigin((int) (screenHeight * DSClientConfig.OverlayY.get()), height(), screenHeight);
    }

    private static int clampOrigin(int origin, int elementSize, int screenSize) {
        return screenSize <= elementSize ? 0 : Mth.clamp(origin, 0, screenSize - elementSize);
    }

    /** 位置编辑页与游戏内 Overlay 共用同一个绘制入口，预览和实际布局不会分叉。 */
    public static void renderBox(GuiGraphics graphics, Font font, FocusSummary summary, int x, int y) {
        List<Line> lines = fittingLines(graphics, font, lines(summary), x, y);
        if(lines.isEmpty()) return;
        int logicalWidth = measureWidth(font, lines);
        int logicalHeight = measureHeight(lines);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        float scale = scale();
        graphics.pose().scale(scale, scale, 1);
        graphics.fill(0, 0, logicalWidth, logicalHeight, background());
        int textY = PADDING;
        for (Line line : lines) {
            graphics.drawString(font, line.text(), PADDING, textY, line.color());
            textY += LINE_HEIGHT;
        }
        graphics.pose().popPose();
    }

    /** 编辑位置时始终有稳定的示例内容，避免空统计导致无法观察尺寸。 */
    public static FocusSummary previewSummary() {
        FocusSummary current = ClientStats.summary();
        if(current.lifetime().metrics().hitCount() > 0) return current;
        MetricsView metrics = new MetricsView(1234.5f, 1500, 0, DamageReduction.NONE, 42, 0,
                230, null, Component.empty(), null, Component.empty(), 0, 8,
                85.6f, 92.1f, 300, 260, 110);
        FocusMetricsView focusMetrics = new FocusMetricsView(metrics,
                new ContributionView(DSKeyLang.CategoryPhysical.copy(), 820, 0.66f),
                new ContributionView(EntityType.ARROW.getDescription(), 760, 0.61f));
        return new FocusSummary(0, new FocusScopeView(0, Optional.empty(), Optional.empty(), false,
                EntityType.PLAYER.getDescription(), EntityType.ZOMBIE.getDescription()), focusMetrics, focusMetrics, true, "");
    }

    private static List<Line> lines(FocusSummary summary) {
        List<Line> lines = new ArrayList<>();
        int valueColor = summary.active() ? TEXT_COLOR : INACTIVE_COLOR;
        if(DSClientConfig.OverlayShowFocus.get()) {
            lines.add(new Line(DSKeyLang.OverlayFocus.get(summary.scope().sourceName(), summary.scope().targetName()), TITLE_COLOR));
        }
        if(DSClientConfig.OverlayShowActualDamage.get()) {
            MetricsView metrics = metrics(summary, DSClientConfig.OverlayActualDamageScope.get());
            lines.add(new Line(DSKeyLang.OverlayFinalDamage.getNumber1f(metrics.totalActual()), valueColor));
        }
        if(DSClientConfig.OverlayShowOriginalDamage.get()) {
            MetricsView metrics = metrics(summary, DSClientConfig.OverlayOriginalDamageScope.get());
            lines.add(new Line(DSKeyLang.OverlayOriginalDamage.getNumber1f(metrics.totalOriginal()), valueColor));
        }
        if(DSClientConfig.OverlayShowReduction.get()) {
            MetricsView metrics = metrics(summary, DSClientConfig.OverlayReductionScope.get());
            lines.add(new Line(DSKeyLang.OverlayReduction.getNumber1f(metrics.reducedDamage(), metrics.reductionRate() * 100), valueColor));
        }
        if(DSClientConfig.OverlayShowActualAverageDps.get()) {
            lines.add(new Line(DSKeyLang.OverlayFinalAverageDps.getNumber1f(
                    metrics(summary, DSClientConfig.OverlayActualAverageDpsScope.get()).averageDps()), valueColor));
        }
        if(DSClientConfig.OverlayShowActualRealtimeDps.get()) {
            lines.add(new Line(DSKeyLang.OverlayFinalRealtimeDps.getNumber1f(
                    metrics(summary, DSClientConfig.OverlayActualRealtimeDpsScope.get()).realtimeDps()), valueColor));
        }
        if(DSClientConfig.OverlayShowOriginalAverageDps.get()) {
            lines.add(new Line(DSKeyLang.OverlayOriginalAverageDps.getNumber1f(
                    metrics(summary, DSClientConfig.OverlayOriginalAverageDpsScope.get()).averageOriginalDps()), valueColor));
        }
        if(DSClientConfig.OverlayShowOriginalRealtimeDps.get()) {
            lines.add(new Line(DSKeyLang.OverlayOriginalRealtimeDps.getNumber1f(
                    metrics(summary, DSClientConfig.OverlayOriginalRealtimeDpsScope.get()).realtimeOriginalDps()), valueColor));
        }
        if(DSClientConfig.OverlayShowHits.get()) {
            lines.add(new Line(DSKeyLang.OverlayHits.get(metrics(summary, DSClientConfig.OverlayHitsScope.get()).hitCount()), valueColor));
        }
        if(DSClientConfig.OverlayShowAverageHit.get()) {
            lines.add(new Line(DSKeyLang.OverlayAverageHit.getNumber1f(
                    metrics(summary, DSClientConfig.OverlayAverageHitScope.get()).averageDamage()), valueColor));
        }
        addHighestDamage(lines, summary, valueColor);
        if(DSClientConfig.OverlayShowTopDamageType.get()) {
            ContributionView contribution = scoped(summary, DSClientConfig.OverlayTopDamageTypeScope.get()).topDamageType();
            lines.add(new Line(DSKeyLang.OverlayTopDamageType.getNumber1f(
                    contribution.name(), contribution.damage(), contribution.share() * 100), valueColor));
        }
        if(DSClientConfig.OverlayShowTopDirectSource.get()) {
            ContributionView contribution = scoped(summary, DSClientConfig.OverlayTopDirectSourceScope.get()).topDirectSource();
            lines.add(new Line(DSKeyLang.OverlayTopDirectSource.getNumber1f(
                    contribution.name(), contribution.damage(), contribution.share() * 100), valueColor));
        }
        if(DSClientConfig.OverlayShowSessionStatus.get()) {
            Component status = summary.active()
                    ? DSKeyLang.OverlaySessionActive.getNumber1f(summary.session().metrics().durationSeconds())
                    : DSKeyLang.OverlaySessionInactive.copy();
            lines.add(new Line(status, valueColor));
        }
        return lines;
    }

    private static void addHighestDamage(List<Line> lines, FocusSummary summary, int valueColor) {
        boolean original = DSClientConfig.OverlayShowMaxOriginal.get();
        boolean actual = DSClientConfig.OverlayShowMaxActual.get();
        if(!original && !actual) return;
        if(original && actual) {
            float maxOriginal = metrics(summary, DSClientConfig.OverlayMaxOriginalScope.get()).maxOriginal();
            float maxActual = metrics(summary, DSClientConfig.OverlayMaxActualScope.get()).maxSingle();
            lines.add(new Line(DSKeyLang.OverlayHighestBoth.getNumber1f(maxOriginal, maxActual), valueColor));
            return;
        }
        if(original) {
            lines.add(new Line(DSKeyLang.OverlayHighestOriginal.getNumber1f(
                    metrics(summary, DSClientConfig.OverlayMaxOriginalScope.get()).maxOriginal()), valueColor));
            return;
        }
        lines.add(new Line(DSKeyLang.OverlayHighestFinal.getNumber1f(
                metrics(summary, DSClientConfig.OverlayMaxActualScope.get()).maxSingle()), valueColor));
    }

    private static MetricsView metrics(FocusSummary summary, OverlayMetricScope scope) {
        return scoped(summary, scope).metrics();
    }

    private static FocusMetricsView scoped(FocusSummary summary, OverlayMetricScope scope) {
        return scope == OverlayMetricScope.LIFETIME ? summary.lifetime() : summary.session();
    }

    private static int measureWidth(Font font, FocusSummary summary) {
        return measureWidth(font, lines(summary));
    }

    private static int measureWidth(Font font, List<Line> lines) {
        int textWidth = lines.stream().mapToInt(line -> font.width(line.text())).max().orElse(0);
        return Math.max(MIN_WIDTH, textWidth + PADDING * 2);
    }

    private static int measureHeight(FocusSummary summary) {
        return measureHeight(lines(summary));
    }

    private static int measureHeight(List<Line> lines) {
        return PADDING * 2 + lines.size() * LINE_HEIGHT;
    }

    private static int scaled(int size) {
        return Math.max(1, (int) Math.ceil(size * scale()));
    }

    private static float scale() {
        return DSClientConfig.OverlayScale.get().floatValue();
    }

    private static int background() {
        return (int) Math.round(DSClientConfig.OverlayBackgroundOpacity.get() * 255) << 24;
    }

    /** Overlay 不接收输入，溢出时优先保留靠前字段并裁剪文本，不能盖出可用游戏界面。 */
    private static List<Line> fittingLines(GuiGraphics graphics, Font font, List<Line> original, int x, int y) {
        float scale = scale();
        int logicalWidth = Math.max(PADDING * 2 + 1, (int) Math.floor((graphics.guiWidth() - x) / scale));
        int logicalHeight = Math.max(PADDING * 2 + LINE_HEIGHT, (int) Math.floor((graphics.guiHeight() - y) / scale));
        int maxTextWidth = Math.max(1, logicalWidth - PADDING * 2);
        int maxLines = Math.max(1, (logicalHeight - PADDING * 2) / LINE_HEIGHT);
        List<Line> fitted = new ArrayList<>(Math.min(original.size(), maxLines));
        for (Line line : original) {
            if(fitted.size() >= maxLines) break;
            fitted.add(new Line(truncate(font, line.text(), maxTextWidth), line.color()));
        }
        return fitted;
    }

    private static Component truncate(Font font, Component text, int maxWidth) {
        if(font.width(text) <= maxWidth) return text;
        String suffix = "...";
        int prefixWidth = Math.max(1, maxWidth - font.width(suffix));
        return Component.literal(font.plainSubstrByWidth(text.getString(), prefixWidth) + suffix);
    }
}
