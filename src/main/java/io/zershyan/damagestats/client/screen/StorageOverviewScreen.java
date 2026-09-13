package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStorageOverview;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.DSPackets;
import io.zershyan.damagestats.registry.packet.StorageCleanupPacket;
import io.zershyan.damagestats.registry.packet.StorageOverviewRequestPacket;
import io.zershyan.damagestats.stats.save.StorageCleanupTarget;
import io.zershyan.damagestats.stats.save.StorageIndexState;
import io.zershyan.damagestats.stats.save.StorageOverview;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** 仅向管理员展示服务端目录摘要，信息区可滚动以适应低高度窗口。 */
public final class StorageOverviewScreen extends Screen {
    private static final int SIDE = 18;
    private static final int PREFERRED_HEADER_HEIGHT = 32;
    private static final int LINE_HEIGHT = 13;
    private static final int BACKGROUND = 0xFF15181C;
    private static final int HEADER = 0xFF20262C;
    private static final int BORDER = 0xFF505A64;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFACB8C2;
    private static final int ACCENT = 0xFF55D6E8;

    private int scrollOffset;
    private StorageOverview displayedOverview;
    private List<Component> displayedLines = List.of();

    public StorageOverviewScreen() {
        super(DSKeyLang.ScreenStorageTitle.copy());
    }

    @Override
    protected void init() {
        ActionLayout actions = actionLayout();
        if(actions.bounds().size() == 3) {
            ScreenLayout.Bounds temporary = actions.bounds().get(0);
            ScreenLayout.Bounds exports = actions.bounds().get(1);
            ScreenLayout.Bounds all = actions.bounds().get(2);
            addRenderableWidget(Button.builder(DSKeyLang.ScreenStorageCleanupTemporary.copy(), button ->
                            confirm(StorageCleanupTarget.TEMPORARY_AND_BACKUPS))
                    .bounds(temporary.x(), temporary.y(), temporary.width(), temporary.height()).build());
            addRenderableWidget(Button.builder(DSKeyLang.ScreenStorageCleanupExports.copy(), button ->
                            confirm(StorageCleanupTarget.EXPORTS))
                    .bounds(exports.x(), exports.y(), exports.width(), exports.height()).build());
            addRenderableWidget(Button.builder(DSKeyLang.ScreenStorageCleanupAll.copy(), button ->
                            confirm(StorageCleanupTarget.ALL_RECORDS))
                    .bounds(all.x(), all.y(), all.width(), all.height()).build());
        }
        ScreenLayout.Flow footer = footerFlow();
        ScreenLayout.Bounds refresh = footer.bounds().get(0);
        ScreenLayout.Bounds done = footer.bounds().get(footer.bounds().size() - 1);
        addRenderableWidget(Button.builder(DSKeyLang.ScreenStorageRefresh.copy(), button -> refresh())
                .bounds(refresh.x(), refresh.y(), refresh.width(), refresh.height()).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(done.x(), done.y(), done.width(), done.height()).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        int headerHeight = headerHeight();
        ScreenLayout.Flow footer = footerFlow();
        ActionLayout actions = actionLayout();
        int footerTop = footer.top();
        graphics.fill(0, 0, width, height, BACKGROUND);
        graphics.fill(0, 0, width, headerHeight, HEADER);
        graphics.fill(0, footerTop, width, height, HEADER);
        if(headerHeight > 0) graphics.fill(0, headerHeight - 1, width, headerHeight, BORDER);
        if(footerTop < height) graphics.fill(0, footerTop, width, Math.min(height, footerTop + 1), BORDER);
        if(headerHeight >= 10) {
            graphics.drawCenteredString(font, title, width / 2,
                    Mth.clamp(11, 0, Math.max(0, headerHeight - 1)), TEXT);
        }

        StorageOverview overview = ClientStorageOverview.overview();
        if(displayedOverview != overview) {
            displayedOverview = overview;
            displayedLines = lines(overview);
        }
        List<Component> lines = displayedLines;
        int top = headerHeight + 8;
        int bottom = actions.top() - 8;
        int visible = Math.max(0, (bottom - top) / LINE_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, lines.size() - visible));
        List<Component> tooltip = List.of();
        if(bottom > top && visible > 0) {
            int left = ScreenLayout.left(width, SIDE);
            int right = ScreenLayout.right(width, SIDE);
            int textWidth = Math.max(1, right - left);
            graphics.enableScissor(0, top, width, bottom);
            for(int index = scrollOffset; index < Math.min(lines.size(), scrollOffset + visible); index++) {
                Component line = lines.get(index);
                int y = top + (index - scrollOffset) * LINE_HEIGHT;
                graphics.drawString(font, font.plainSubstrByWidth(line.getString(), textWidth), left, y,
                        index == 0 ? ACCENT : MUTED);
                if(mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + LINE_HEIGHT
                        && font.width(line) > textWidth) tooltip = List.of(line);
            }
            graphics.disableScissor();
        }
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        if(!tooltip.isEmpty()) graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        int headerHeight = headerHeight();
        int actionTop = actionLayout().top();
        if(mouseY < headerHeight || mouseY >= actionTop) return super.mouseScrolled(mouseX, mouseY, scrollY);
        int visible = Math.max(0, (actionTop - 8 - (headerHeight + 8)) / LINE_HEIGHT);
        int maxOffset = Math.max(0, displayedLines.size() - visible);
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxOffset);
        return true;
    }

    void cleanup(StorageCleanupTarget target) {
        DSPackets.sendToServer(new StorageCleanupPacket(target));
    }

    private void confirm(StorageCleanupTarget target) {
        if(minecraft == null) return;
        minecraft.setScreen(new ConfirmStorageCleanupScreen(this, target));
    }

    private void refresh() {
        DSPackets.sendToServer(StorageOverviewRequestPacket.INSTANCE);
    }

    private int headerHeight() {
        return Mth.clamp(Math.min(height / 3, footerFlow().top()), 0, PREFERRED_HEADER_HEIGHT);
    }

    private ScreenLayout.Flow footerFlow() {
        return ScreenLayout.bottomFlow(width, height, SIDE, 20, 4, 6, 80, 84);
    }

    private ActionLayout actionLayout() {
        int[] preferredWidths = {180, 180, 220};
        int top = headerHeight() + 4;
        int bottom = footerFlow().top() - 4;
        int available = bottom - top;
        if(available <= 0) return new ActionLayout(bottom, 0, 0, List.of());
        List<ScreenLayout.Bounds> preferred = ScreenLayout.flow(width, SIDE, 0, 20, 4, preferredWidths);
        int rows = preferred.stream().map(ScreenLayout.Bounds::y).distinct().mapToInt(ignored -> 1).sum();
        if(available < rows) return new ActionLayout(bottom, 0, 0, List.of());
        int gap = rows <= 1 ? 0 : Mth.clamp((available - rows) / (rows - 1), 0, 4);
        int buttonHeight = Mth.clamp((available - gap * (rows - 1)) / rows, 1, 20);
        int used = rows * buttonHeight + gap * (rows - 1);
        int actionTop = bottom - used;
        return new ActionLayout(actionTop, buttonHeight, gap,
                ScreenLayout.flow(width, SIDE, actionTop, buttonHeight, gap, preferredWidths));
    }

    private static List<Component> lines(StorageOverview overview) {
        return List.of(
                DSKeyLang.ScreenStorageTotal.get(bytes(overview.totalBytes())),
                DSKeyLang.ScreenStorageEvents.get(overview.eventCount()),
                DSKeyLang.ScreenStorageSegments.get(overview.segmentCount()),
                DSKeyLang.ScreenStorageIndex.get(indexState(overview.indexState())),
                DSKeyLang.ScreenStorageRawSegments.get(bytes(overview.rawSegmentBytes())),
                DSKeyLang.ScreenStorageCompressedSegments.get(bytes(overview.compressedSegmentBytes())),
                DSKeyLang.ScreenStorageIndexBytes.get(bytes(overview.indexBytes())),
                DSKeyLang.ScreenStorageCache.get(bytes(overview.cacheBytes())),
                DSKeyLang.ScreenStorageExports.get(bytes(overview.exportBytes())),
                DSKeyLang.ScreenStorageTemporary.get(bytes(overview.temporaryBytes())),
                DSKeyLang.ScreenStorageBackups.get(bytes(overview.backupBytes()))
        );
    }

    private static Component indexState(StorageIndexState state) {
        return switch (state) {
            case EMPTY -> DSKeyLang.ScreenStorageIndexEmpty.copy();
            case LOADED -> DSKeyLang.ScreenStorageIndexLoaded.copy();
            case REBUILT -> DSKeyLang.ScreenStorageIndexRebuilt.copy();
            case FAILED -> DSKeyLang.ScreenStorageIndexFailed.copy();
        };
    }

    private static String bytes(long bytes) {
        if(bytes < 1024) return bytes + " B";
        String[] units = {"KiB", "MiB", "GiB", "TiB"};
        double value = bytes;
        int unit = -1;
        while(value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }

    private record ActionLayout(int top, int buttonHeight, int gap, List<ScreenLayout.Bounds> bounds) {}
}
