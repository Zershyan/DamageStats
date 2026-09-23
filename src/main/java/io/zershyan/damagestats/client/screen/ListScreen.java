package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 只读文本列表页的公共外壳：顶栏标题、卡片头（可放操作按钮）、可滚动正文、底栏。
 * 详细指标、历史战斗、服务器存储三个页面结构一致，共用同一套骨架。
 */
abstract class ListScreen extends UiScreen {
    /** 列表里的一行。heading 行会用强调色并额外留出行间距。 */
    protected record Line(Component text, int color, boolean heading) {
        protected static Line heading(Component text) {
            return new Line(text, UiTheme.ACCENT, true);
        }

        protected static Line of(Component text, int color) {
            return new Line(text, color, false);
        }

        protected static Line muted(Component text) {
            return new Line(text, UiTheme.TEXT_MUTED, false);
        }

        protected static Line spacer() {
            return new Line(Component.empty(), UiTheme.TEXT, false);
        }
    }

    private static final int LINE_HEIGHT = 12;
    private static final int HEADING_EXTRA = 3;
    private static final int FOOTER_HEIGHT = 26;
    private static final int CARD_FOOTER_ROW = 20;
    private static final int CARD_FOOTER_GAP = 4;
    private static final int HEADER_ACTION_WIDTH = 62;
    private static final int HEADER_ACTION_HEIGHT = 18;

    private final List<UiButton> headerButtons = new ArrayList<>();
    private final List<UiButton> footerButtons = new ArrayList<>();

    private UiLayout.Rect card = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect viewport = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect scrollTrack = new UiLayout.Rect(0, 0, 0, 0);
    private final UiScroll scroll = new UiScroll();

    protected ListScreen(Component title) {
        super(title);
    }

    /** 列表内容，每帧取一次。 */
    protected abstract List<Line> buildLines();

    /** 顶栏右侧的操作按钮，子类用 {@link #headerSlot} 定位后返回。 */
    protected List<UiButton> buildHeaderActions() {
        return List.of();
    }

    /** 底栏按钮，子类用 {@link #footerSlot} 定位后返回。 */
    protected List<UiButton> buildFooterActions() {
        return List.of();
    }

    /** 卡片底部额外预留的操作行数，用于存储页那排清理按钮。 */
    protected int cardFooterRows() {
        return 0;
    }

    /** 卡片底部第 row 行的位置。 */
    protected final UiLayout.Rect cardFooterSlot(int row) {
        int base = viewport.bottom() + CARD_FOOTER_GAP;
        return new UiLayout.Rect(card.x() + 8, base + row * (CARD_FOOTER_ROW + CARD_FOOTER_GAP),
                Math.max(0, card.width() - 16), CARD_FOOTER_ROW);
    }

    /** 顶栏里右对齐的按钮槽位，index 从右往左数。 */
    protected final UiLayout.Rect headerSlot(int index, int total, int preferredWidth) {
        int count = Math.max(1, total);
        // 区域按实际按钮宽度预算，再夹到窗口内；预算和列宽不一致的话右对齐会落空
        int span = Math.min(count * preferredWidth + (count - 1) * 5,
                Math.max(1, width - PAGE_PADDING * 2));
        UiLayout.Rect area = new UiLayout.Rect(Math.max(PAGE_PADDING, topBar().right() - PAGE_PADDING - span),
                topBar().y() + (topBar().height() - HEADER_ACTION_HEIGHT) / 2, span, HEADER_ACTION_HEIGHT);
        int[] widths = new int[count];
        for(int slot = 0; slot < count; slot++) widths[slot] = preferredWidth;
        List<UiLayout.Rect> slots = UiLayout.row(area, 5, widths);
        return UiLayout.item(slots, index, area);
    }

    /** 顶栏为操作按钮预留的总宽度，交给标题做截断参考。 */
    protected final int headerReservedWidth() {
        int count = headerButtons.size();
        if(count <= 0) return 30;
        return count * HEADER_ACTION_WIDTH + (count - 1) * 5 + 12;
    }

    /** 底栏里右对齐的按钮槽位，index 从右往左数。 */
    protected final UiLayout.Rect footerSlot(int index, int total, int preferredWidth) {
        int barHeight = Mth.clamp(FOOTER_HEIGHT, 0, Math.max(0, height));
        UiLayout.Rect bar = new UiLayout.Rect(PAGE_PADDING, height - barHeight + 4,
                Math.max(0, width - PAGE_PADDING * 2), Math.max(1, barHeight - 8));
        int count = Math.max(1, total);
        int[] widths = new int[count];
        for(int slot = 0; slot < count; slot++) widths[slot] = preferredWidth;
        List<UiLayout.Rect> slots = UiLayout.rowRight(bar, 5, widths);
        return UiLayout.item(slots, count - 1 - index, bar);
    }

    @Override
    protected void init() {
        computeLayout();
        headerButtons.clear();
        headerButtons.addAll(buildHeaderActions());
        footerButtons.clear();
        footerButtons.addAll(buildFooterActions());
        headerButtons.forEach(this::addRenderableWidget);
        footerButtons.forEach(this::addRenderableWidget);
    }

    private void computeLayout() {
        // 底栏固定在最下一条，卡片铺满其余空间
        int footerHeight = Mth.clamp(FOOTER_HEIGHT, 0, Math.max(0, height));
        card = new UiLayout.Rect(PAGE_PADDING, topBar().bottom() + 6,
                Math.max(0, width - PAGE_PADDING * 2),
                Math.max(0, height - topBar().bottom() - footerHeight - 12));
        int reserved = cardFooterRows() <= 0 ? 0
                : cardFooterRows() * (CARD_FOOTER_ROW + CARD_FOOTER_GAP) + CARD_FOOTER_GAP;
        viewport = new UiLayout.Rect(card.x() + 8, card.y() + 6,
                Math.max(0, card.width() - 16), Math.max(0, card.height() - 12 - reserved));
        scrollTrack = new UiLayout.Rect(card.right() - 6, viewport.y(), 3, viewport.height());
    }

    @Override
    protected void renderContent(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTopBar(graphics, title, null, null, false, headerReservedWidth());
        renderCard(graphics, card, false);
        renderLines(graphics, mouseX, mouseY);
        renderBottomBar(graphics);
        graphics.flush();
    }

    private void renderLines(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Line> lines = buildLines();
        List<Integer> tops = new ArrayList<>(lines.size());
        int cursor = 0;
        for(Line line : lines) {
            tops.add(cursor);
            cursor += LINE_HEIGHT + (line.heading() ? HEADING_EXTRA : 0);
        }
        if(viewport.isEmpty()) return;
        scroll.measure(Math.max(1, cursor), viewport.height());
        scroll.tick();
        int offset = scroll.offset();
        List<Component> tooltip = List.of();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        for(int index = 0; index < lines.size(); index++) {
            int y = viewport.y() + tops.get(index) - offset;
            if(y + LINE_HEIGHT < viewport.y() || y > viewport.bottom()) continue;
            Line line = lines.get(index);
            if(line.text().getString().isEmpty()) continue;
            String clipped = font.plainSubstrByWidth(line.text().getString(), Math.max(1, viewport.width() - 6));
            if(line.heading()) {
                // 分组标题用加粗 + 左侧强调短条，和正文拉开层级
                UiDraw.rect(graphics, viewport.x(), y + 3F, 2F, LINE_HEIGHT - 5F, UiTheme.ACCENT);
                UiText.bold(graphics, Component.literal(clipped), viewport.x() + 7, y + 2, line.color());
            } else {
                UiText.plain(graphics, Component.literal(clipped), viewport.x(), y + 2, line.color());
            }
            if(mouseY >= y && mouseY < y + LINE_HEIGHT && mouseX >= viewport.x()
                    && mouseX <= viewport.right() && font.width(line.text()) > viewport.width() - 6) {
                tooltip = List.of(line.text());
            }
        }
        graphics.disableScissor();
        scroll.renderIndicator(graphics, scrollTrack);
        if(!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if(!viewport.contains(mouseX, mouseY)) return super.mouseScrolled(mouseX, mouseY, scrollY);
        scroll.scrollBy(Math.signum(scrollY), LINE_HEIGHT * 2);
        return true;
    }
}
