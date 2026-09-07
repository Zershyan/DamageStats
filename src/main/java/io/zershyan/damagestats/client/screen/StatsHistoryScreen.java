package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.HistoryRequestPacket;
import io.zershyan.damagestats.stats.view.HistoryPage;
import io.zershyan.damagestats.stats.view.SessionView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 已结束战斗的可滚动摘要页，当前战斗仍在焦点页头展示。 */
public final class StatsHistoryScreen extends Screen {
    private static final int SIDE = 18;
    private static final int PREFERRED_HEADER_HEIGHT = 32;
    private static final int ROW_HEIGHT = 14;
    private static final int BACKGROUND = 0xE015181C;
    private static final int HEADER = 0xEE20262C;
    private static final int BORDER = 0xFF505A64;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFACB8C2;
    private static final int ACCENT = 0xFF55D6E8;

    private final StatsScreen parent;
    private int requestId;
    private int scrollOffset;
    private @Nullable HistoryPage adopted;
    private List<Component> tooltip = List.of();

    public StatsHistoryScreen(StatsScreen parent) {
        super(DSKeyLang.ScreenHistory.copy());
        this.parent = parent;
    }

    @Override
    protected void init() {
        ScreenLayout.Flow footer = footerFlow();
        ScreenLayout.Bounds refresh = footer.bounds().getFirst();
        ScreenLayout.Bounds done = footer.bounds().getLast();
        addRenderableWidget(Button.builder(DSKeyLang.ScreenRefresh.copy(), button -> request())
                .bounds(refresh.x(), refresh.y(), refresh.width(), refresh.height()).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> minecraft.setScreen(parent))
                .bounds(done.x(), done.y(), done.width(), done.height()).build());
        request();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int headerHeight = headerHeight();
        ScreenLayout.Flow footer = footerFlow();
        int footerTop = footer.top();
        HistoryPage page = ClientStats.historyPage();
        if(page != null && page.requestId() == requestId && page != adopted) {
            adopted = page;
            scrollOffset = 0;
        }
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, headerHeight, HEADER);
        graphics.fill(0, footerTop, width, height, HEADER);
        if(headerHeight > 0) graphics.fill(0, headerHeight - 1, width, headerHeight, BORDER);
        if(footerTop < height) graphics.fill(0, footerTop, width, Math.min(height, footerTop + 1), BORDER);
        if(headerHeight >= 10) {
            graphics.drawCenteredString(font, title, width / 2,
                    Math.clamp(11, 0, Math.max(0, headerHeight - 1)), TEXT);
        }

        List<HistoryLine> lines = lines(page != null && page.requestId() == requestId ? page : null);
        int top = headerHeight + 6;
        int bottom = footerTop - 6;
        int visible = Math.max(0, (bottom - top) / ROW_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, lines.size() - visible));
        tooltip = List.of();
        if(bottom > top && visible > 0) {
            int left = ScreenLayout.left(width, SIDE);
            int right = ScreenLayout.right(width, SIDE);
            int textWidth = Math.max(1, right - left);
            graphics.enableScissor(0, top, width, bottom);
            for(int index = scrollOffset; index < Math.min(lines.size(), scrollOffset + visible); index++) {
                HistoryLine line = lines.get(index);
                int y = top + (index - scrollOffset) * ROW_HEIGHT;
                graphics.drawString(font, font.plainSubstrByWidth(line.text().getString(), textWidth), left, y, line.color());
                if(mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + ROW_HEIGHT
                        && font.width(line.text()) > textWidth) tooltip = List.of(line.text());
            }
            graphics.disableScissor();
        }
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        if(!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int headerHeight = headerHeight();
        int footerTop = footerFlow().top();
        if(mouseY < headerHeight || mouseY >= footerTop) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int visible = Math.max(0, (footerTop - 6 - (headerHeight + 6)) / ROW_HEIGHT);
        int maxOffset = Math.max(0, lines(adopted).size() - visible);
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxOffset);
        return true;
    }

    private int headerHeight() {
        return Math.min(PREFERRED_HEADER_HEIGHT, Math.min(Math.max(0, height / 3), footerFlow().top()));
    }

    private ScreenLayout.Flow footerFlow() {
        return ScreenLayout.bottomFlow(width, height, SIDE, 20, 4, 6, 72, 84);
    }

    private void request() {
        requestId = ClientStats.nextHistoryRequestId();
        adopted = null;
        scrollOffset = 0;
        PacketDistributor.sendToServer(new HistoryRequestPacket(parent.browsingFilter(), requestId));
    }

    private static List<HistoryLine> lines(@Nullable HistoryPage page) {
        if(page == null) return List.of(new HistoryLine(DSKeyLang.NoData.copy(), MUTED));
        if(!page.allowed()) return List.of(new HistoryLine(DSKeyLang.StatsPrivate.copy(), MUTED));
        if(page.outgoing().isEmpty() && page.incoming().isEmpty()) return List.of(new HistoryLine(DSKeyLang.NoData.copy(), MUTED));
        List<HistoryLine> lines = new ArrayList<>();
        append(lines, DSKeyLang.TitleOutgoing.copy(), page.outgoing());
        append(lines, DSKeyLang.TitleIncoming.copy(), page.incoming());
        return lines;
    }

    private static void append(List<HistoryLine> lines, Component heading, List<SessionView> sessions) {
        if(sessions.isEmpty()) return;
        lines.add(new HistoryLine(heading, ACCENT));
        for (int index = 0; index < sessions.size(); index++) {
            SessionView session = sessions.get(index);
            lines.add(new HistoryLine(DSKeyLang.SessionLine.getNumber1f(index + 1, session.totalDamage(),
                    session.averageDps(), session.durationSeconds(), session.hitCount()), TEXT));
        }
        lines.add(new HistoryLine(Component.empty(), TEXT));
    }

    private record HistoryLine(Component text, int color) {}
}
