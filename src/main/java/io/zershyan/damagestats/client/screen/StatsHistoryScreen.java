package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.ui.UiButton;
import io.zershyan.damagestats.client.ui.UiDraw;
import io.zershyan.damagestats.client.ui.UiTheme;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.DSPackets;
import io.zershyan.damagestats.registry.packet.HistoryRequestPacket;
import io.zershyan.damagestats.stats.view.HistoryPage;
import io.zershyan.damagestats.stats.view.SessionView;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 已结束战斗的可滚动摘要页，当前战斗仍在焦点页头展示。 */
public final class StatsHistoryScreen extends ListScreen {
    private final StatsScreen parent;
    private int requestId;
    private @Nullable HistoryPage displayed;
    private List<Line> displayedLines = List.of();

    public StatsHistoryScreen(StatsScreen parent) {
        super(DSKeyLang.ScreenHistory.copy());
        this.parent = parent;
    }

    @Override
    protected List<Line> buildLines() {
        return displayedLines;
    }

    @Override
    protected List<UiButton> buildHeaderActions() {
        return List.of(UiButton.builder(DSKeyLang.ScreenRefresh.copy(), button -> request())
                .bounds(headerSlot(0, 1, 62)).variant(UiButton.Variant.GHOST).icon(UiDraw::refresh).build());
    }

    @Override
    protected List<UiButton> buildFooterActions() {
        return List.of(UiButton.builder(CommonComponents.GUI_DONE, button -> minecraft.setScreen(parent))
                .bounds(footerSlot(0, 1, 92)).variant(UiButton.Variant.SECONDARY).build());
    }

    @Override
    protected void init() {
        super.init();
        request();
    }

    @Override
    protected void renderContent(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        HistoryPage page = ClientStats.historyPage();
        if(page != null && page.requestId() == requestId && page != displayed) {
            displayed = page;
            displayedLines = lines(page);
        }
        super.renderContent(graphics, mouseX, mouseY, partialTick);
    }

    private void request() {
        requestId = ClientStats.nextHistoryRequestId();
        displayed = null;
        displayedLines = lines(null);
        DSPackets.sendToServer(new HistoryRequestPacket(parent.browsingFilter(), requestId));
    }

    private static List<Line> lines(@Nullable HistoryPage page) {
        if(page == null) return List.of(Line.muted(DSKeyLang.NoData.copy()));
        if(!page.allowed()) return List.of(Line.muted(DSKeyLang.StatsPrivate.copy()));
        if(page.outgoing().isEmpty() && page.incoming().isEmpty()) {
            return List.of(Line.muted(DSKeyLang.NoData.copy()));
        }
        List<Line> lines = new ArrayList<>();
        append(lines, DSKeyLang.TitleOutgoing.copy(), page.outgoing());
        append(lines, DSKeyLang.TitleIncoming.copy(), page.incoming());
        return lines;
    }

    private static void append(List<Line> lines, Component heading, List<SessionView> sessions) {
        if(sessions.isEmpty()) return;
        lines.add(Line.heading(heading));
        for(int index = 0; index < sessions.size(); index++) {
            SessionView session = sessions.get(index);
            lines.add(Line.of(DSKeyLang.SessionLine.getNumber1f(index + 1, session.totalDamage(),
                    session.averageDps(), session.durationSeconds(), session.hitCount()), UiTheme.TEXT));
        }
        lines.add(Line.spacer());
    }
}
