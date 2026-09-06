package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStorageOverview;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

/** 仅向管理员展示服务端目录摘要，信息区可滚动以适应低高度窗口。 */
public final class StorageOverviewScreen extends Screen {
    private static final int SIDE = 18;
    private static final int HEADER_HEIGHT = 32;
    private static final int FOOTER_HEIGHT = 32;
    private static final int LINE_HEIGHT = 13;
    private static final int BACKGROUND = 0xE015181C;
    private static final int HEADER = 0xEE20262C;
    private static final int BORDER = 0xFF505A64;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int MUTED = 0xFFACB8C2;
    private static final int ACCENT = 0xFF55D6E8;

    private int scrollOffset;

    public StorageOverviewScreen() {
        super(DSKeyLang.ScreenStorageTitle.copy());
    }

    @Override
    protected void init() {
        int actionWidth = Math.min(180, (width - SIDE * 2 - 4) / 2);
        int left = width / 2 - actionWidth - 2;
        int right = width / 2 + 2;
        int actionTop = actionTop();
        addRenderableWidget(Button.builder(DSKeyLang.ScreenStorageCleanupTemporary.copy(), button ->
                        confirm(StorageCleanupTarget.TEMPORARY_AND_BACKUPS))
                .bounds(left, actionTop, actionWidth, 20).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenStorageCleanupExports.copy(), button ->
                        confirm(StorageCleanupTarget.EXPORTS))
                .bounds(right, actionTop, actionWidth, 20).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenStorageCleanupAll.copy(), button ->
                        confirm(StorageCleanupTarget.ALL_RECORDS))
                .bounds(width / 2 - Math.min(220, width - SIDE * 2) / 2, actionTop + 24,
                        Math.min(220, width - SIDE * 2), 20).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenStorageRefresh.copy(), button -> refresh())
                .bounds(SIDE, height - FOOTER_HEIGHT + 6, 80, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
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

        List<Component> lines = lines(ClientStorageOverview.overview());
        int top = HEADER_HEIGHT + 8;
        int bottom = actionTop() - 8;
        int visible = Math.max(1, (bottom - top) / LINE_HEIGHT);
        scrollOffset = Mth.clamp(scrollOffset, 0, Math.max(0, lines.size() - visible));
        for (int index = scrollOffset; index < Math.min(lines.size(), scrollOffset + visible); index++) {
            Component line = lines.get(index);
            int y = top + (index - scrollOffset) * LINE_HEIGHT;
            graphics.drawString(font, font.plainSubstrByWidth(line.getString(), Math.max(1, width - SIDE * 2)), SIDE, y,
                    index == 0 ? ACCENT : MUTED);
        }
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(mouseY < HEADER_HEIGHT || mouseY >= actionTop() - 4) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int visible = Math.max(1, (actionTop() - 8 - (HEADER_HEIGHT + 8)) / LINE_HEIGHT);
        int maxOffset = Math.max(0, lines(ClientStorageOverview.overview()).size() - visible);
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxOffset);
        return true;
    }

    void cleanup(StorageCleanupTarget target) {
        PacketDistributor.sendToServer(new StorageCleanupPacket(target));
    }

    private void confirm(StorageCleanupTarget target) {
        minecraft.setScreen(new ConfirmStorageCleanupScreen(this, target));
    }

    private void refresh() {
        PacketDistributor.sendToServer(StorageOverviewRequestPacket.INSTANCE);
    }

    private int actionTop() {
        return height - FOOTER_HEIGHT - 52;
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
}
