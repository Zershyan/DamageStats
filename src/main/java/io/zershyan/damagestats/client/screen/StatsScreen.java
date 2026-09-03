package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.view.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 统计主界面。数据全部来自服务端推来的快照（{@link ClientStats}），本界面只负责摆放和绘制。
 * 三组切换按钮对应需求里的三个筛选维度：造成/承受、本场/累计、类型/来源/对手。
 */
public class StatsScreen extends Screen {
    private enum Dimension { TYPES, SOURCES, OPPONENTS }

    private static final int WIDTH = 320;
    private static final int HEIGHT = 222;
    private static final int MARGIN = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int ROW_HEIGHT = 10;
    private static final int VISIBLE_ROWS = 8;
    private static final int SMALL_BUTTON_HEIGHT = 16;
    private static final int TOGGLE_WIDTH = 52;
    private static final int DIMENSION_WIDTH = 68;
    private static final int BOTTOM_BUTTON_WIDTH = 84;
    private static final int BOTTOM_BUTTON_HEIGHT = 20;
    private static final int BACKGROUND = 0xE0101010;
    private static final int BORDER = 0xFF5A5A5A;
    private static final int SEPARATOR = 0xFF3A3A3A;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int ACCENT_COLOR = 0xFF55FFFF;

    private boolean outgoing = true;
    private boolean sessionScope = true;
    private Dimension dimension = Dimension.TYPES;
    private int scrollOffset;

    private @Nullable Button outgoingButton;
    private @Nullable Button incomingButton;
    private @Nullable Button sessionButton;
    private @Nullable Button lifetimeButton;
    private @Nullable Button typesButton;
    private @Nullable Button sourcesButton;
    private @Nullable Button opponentsButton;

    public StatsScreen() {
        super(DSKeyLang.ScreenTitle.copy());
    }

    @Override
    protected void init() {
        int left = left();
        int top = top();
        int toggleY = top + 22;
        outgoingButton = addRenderableWidget(Button.builder(DSKeyLang.TabOutgoing.copy(), button -> selectDirection(true))
                .bounds(left + MARGIN, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());
        incomingButton = addRenderableWidget(Button.builder(DSKeyLang.TabIncoming.copy(), button -> selectDirection(false))
                .bounds(left + MARGIN + TOGGLE_WIDTH + 2, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());
        sessionButton = addRenderableWidget(Button.builder(DSKeyLang.ScopeSession.copy(), button -> selectScope(true))
                .bounds(left + WIDTH - MARGIN - TOGGLE_WIDTH * 2 - 2, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());
        lifetimeButton = addRenderableWidget(Button.builder(DSKeyLang.ScopeLifetime.copy(), button -> selectScope(false))
                .bounds(left + WIDTH - MARGIN - TOGGLE_WIDTH, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());

        int dimensionY = top + 94;
        typesButton = addRenderableWidget(Button.builder(DSKeyLang.DimensionTypes.copy(), button -> selectDimension(Dimension.TYPES))
                .bounds(left + MARGIN, dimensionY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());
        sourcesButton = addRenderableWidget(Button.builder(DSKeyLang.DimensionSources.copy(), button -> selectDimension(Dimension.SOURCES))
                .bounds(left + MARGIN + DIMENSION_WIDTH + 2, dimensionY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());
        opponentsButton = addRenderableWidget(Button.builder(DSKeyLang.DimensionOpponents.copy(), button -> selectDimension(Dimension.OPPONENTS))
                .bounds(left + MARGIN + (DIMENSION_WIDTH + 2) * 2, dimensionY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());

        int bottomY = top + HEIGHT - 26;
        addRenderableWidget(Button.builder(DSKeyLang.ScreenEditOverlay.copy(),
                        button -> minecraft.setScreen(new OverlayPositionScreen()))
                .bounds(left + WIDTH - MARGIN - BOTTOM_BUTTON_WIDTH * 2 - 4, bottomY,
                        BOTTOM_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(net.minecraft.network.chat.CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left + WIDTH - MARGIN - BOTTOM_BUTTON_WIDTH, bottomY,
                        BOTTOM_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT).build());

        refreshButtonStates();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = left();
        int top = top();
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, BACKGROUND);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, BORDER);
        graphics.drawCenteredString(font, title, left + WIDTH / 2, top + MARGIN, VALUE_COLOR);

        StatsSnapshot snapshot = ClientStats.snapshot();
        if(snapshot == null) {
            graphics.drawCenteredString(font, DSKeyLang.NoData.copy(),
                    left + WIDTH / 2, top + HEIGHT / 2, LABEL_COLOR);
        } else {
            StatsView view = currentView(snapshot);
            graphics.fill(left + MARGIN, top + 42, left + WIDTH - MARGIN, top + 43, SEPARATOR);
            renderMetrics(graphics, view.metrics(), left + MARGIN, top + 48);
            graphics.fill(left + MARGIN, top + 88, left + WIDTH - MARGIN, top + 89, SEPARATOR);
            renderGroups(graphics, currentGroups(view), left + MARGIN, top + 116);
        }
        // 面板背景在上面才画完，按钮必须放在它之后，否则会被盖住
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }

    private void renderMetrics(GuiGraphics graphics, MetricsView metrics, int x, int y) {
        int column = x + 156;
        graphics.drawString(font, DSKeyLang.TotalDamage.getNumber2f(metrics.totalActual()), x, y, ACCENT_COLOR);
        graphics.drawString(font, DSKeyLang.HitCount.get(metrics.hitCount()), column, y, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.OverlayDps.getNumber1f(metrics.averageDps(), metrics.realtimeDps()),
                x, y + LINE_HEIGHT, ACCENT_COLOR);
        graphics.drawString(font, DSKeyLang.AverageDamage.getNumber2f(metrics.averageDamage()),
                column, y + LINE_HEIGHT, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.MaxSingle.getNumber2f(metrics.maxSingle(), metrics.maxSingleTypeName()),
                x, y + LINE_HEIGHT * 2, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.ReductionRate.getNumber2f(
                metrics.reducedDamage(), metrics.reductionRate() * 100), column, y + LINE_HEIGHT * 2, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.Duration.getNumber1f(metrics.durationSeconds()),
                x, y + LINE_HEIGHT * 3, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.KillCount.get(metrics.killCount()),
                column, y + LINE_HEIGHT * 3, LABEL_COLOR);
    }

    private void renderGroups(GuiGraphics graphics, List<GroupView> groups, int x, int y) {
        if(groups.isEmpty()) {
            graphics.drawString(font, DSKeyLang.NoData.copy(), x, y, LABEL_COLOR);
            return;
        }
        int rows = Math.min(VISIBLE_ROWS, groups.size() - scrollOffset);
        for (int row = 0; row < rows; row++) {
            GroupView group = groups.get(scrollOffset + row);
            graphics.drawString(font, DSKeyLang.DetailLine.getNumber2f(
                            group.name(), group.damage(), group.share() * 100, group.hitCount()),
                    x, y + row * ROW_HEIGHT, LABEL_COLOR);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        StatsSnapshot snapshot = ClientStats.snapshot();
        if(snapshot == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int maxOffset = Math.max(0, currentGroups(currentView(snapshot)).size() - VISIBLE_ROWS);
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxOffset);
        return true;
    }

    private void selectDirection(boolean toOutgoing) {
        outgoing = toOutgoing;
        scrollOffset = 0;
        refreshButtonStates();
    }

    private void selectScope(boolean toSession) {
        sessionScope = toSession;
        scrollOffset = 0;
        refreshButtonStates();
    }

    private void selectDimension(Dimension toDimension) {
        dimension = toDimension;
        scrollOffset = 0;
        refreshButtonStates();
    }

    /** 当前选中的那个按钮置灰，看起来像被按下，省掉一套自绘高亮 */
    private void refreshButtonStates() {
        if(outgoingButton != null) outgoingButton.active = !outgoing;
        if(incomingButton != null) incomingButton.active = outgoing;
        if(sessionButton != null) sessionButton.active = !sessionScope;
        if(lifetimeButton != null) lifetimeButton.active = sessionScope;
        if(typesButton != null) typesButton.active = dimension != Dimension.TYPES;
        if(sourcesButton != null) sourcesButton.active = dimension != Dimension.SOURCES;
        if(opponentsButton != null) opponentsButton.active = dimension != Dimension.OPPONENTS;
    }

    private StatsView currentView(StatsSnapshot snapshot) {
        EntryView entry = outgoing ? snapshot.outgoing() : snapshot.incoming();
        return sessionScope ? entry.session() : entry.lifetime();
    }

    private List<GroupView> currentGroups(StatsView view) {
        return switch (dimension) {
            case TYPES -> view.byType();
            case SOURCES -> view.bySource();
            case OPPONENTS -> view.byOpponent();
        };
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }
}
