package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStorageOverview;
import io.zershyan.damagestats.client.ui.UiButton;
import io.zershyan.damagestats.client.ui.UiDraw;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.DSPackets;
import io.zershyan.damagestats.registry.packet.StorageCleanupPacket;
import io.zershyan.damagestats.registry.packet.StorageOverviewRequestPacket;
import io.zershyan.damagestats.stats.save.StorageCleanupTarget;
import io.zershyan.damagestats.stats.save.StorageIndexState;
import io.zershyan.damagestats.stats.save.StorageOverview;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** 仅向管理员展示服务端目录摘要；清理属于破坏性操作，统一放在卡片底部的独立一列。 */
public final class StorageOverviewScreen extends ListScreen {
    private StorageOverview displayedOverview;
    private List<Line> displayedLines = List.of();

    public StorageOverviewScreen() {
        super(DSKeyLang.ScreenStorageTitle.copy());
    }

    @Override
    protected int cardFooterRows() {
        return 3;
    }

    @Override
    protected List<Line> buildLines() {
        StorageOverview overview = ClientStorageOverview.overview();
        if(displayedOverview != overview) {
            displayedOverview = overview;
            displayedLines = lines(overview);
        }
        return displayedLines;
    }

    @Override
    protected List<UiButton> buildHeaderActions() {
        return List.of(UiButton.builder(DSKeyLang.ScreenStorageRefresh.copy(), button -> refresh())
                .bounds(headerSlot(0, 1, 62)).variant(UiButton.Variant.GHOST).icon(UiDraw::refresh).build());
    }

    @Override
    protected List<UiButton> buildFooterActions() {
        return List.of(UiButton.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(footerSlot(0, 1, 92)).variant(UiButton.Variant.SECONDARY).build());
    }

    @Override
    protected void init() {
        super.init();
        // 三个清理入口纵向排列，全部记录用危险色，避免和另外两个同等显眼
        addRenderableWidget(UiButton.builder(DSKeyLang.ScreenStorageCleanupTemporary.copy(), button ->
                        confirm(StorageCleanupTarget.TEMPORARY_AND_BACKUPS))
                .bounds(cardFooterSlot(0)).variant(UiButton.Variant.OUTLINE).align(UiButton.Align.LEFT)
                .icon(UiDraw::trash).build());
        addRenderableWidget(UiButton.builder(DSKeyLang.ScreenStorageCleanupExports.copy(), button ->
                        confirm(StorageCleanupTarget.EXPORTS))
                .bounds(cardFooterSlot(1)).variant(UiButton.Variant.OUTLINE).align(UiButton.Align.LEFT)
                .icon(UiDraw::trash).build());
        addRenderableWidget(UiButton.builder(DSKeyLang.ScreenStorageCleanupAll.copy(), button ->
                        confirm(StorageCleanupTarget.ALL_RECORDS))
                .bounds(cardFooterSlot(2)).variant(UiButton.Variant.DANGER).align(UiButton.Align.LEFT)
                .icon(UiDraw::trash).build());
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

    private static List<Line> lines(StorageOverview overview) {
        return List.of(
                Line.heading(DSKeyLang.ScreenStorageTotal.get(bytes(overview.totalBytes()))),
                Line.muted(DSKeyLang.ScreenStorageEvents.get(overview.eventCount())),
                Line.muted(DSKeyLang.ScreenStorageSegments.get(overview.segmentCount())),
                Line.muted(DSKeyLang.ScreenStorageIndex.get(indexState(overview.indexState()))),
                Line.muted(DSKeyLang.ScreenStorageRawSegments.get(bytes(overview.rawSegmentBytes()))),
                Line.muted(DSKeyLang.ScreenStorageCompressedSegments.get(bytes(overview.compressedSegmentBytes()))),
                Line.muted(DSKeyLang.ScreenStorageIndexBytes.get(bytes(overview.indexBytes()))),
                Line.muted(DSKeyLang.ScreenStorageCache.get(bytes(overview.cacheBytes()))),
                Line.muted(DSKeyLang.ScreenStorageExports.get(bytes(overview.exportBytes()))),
                Line.muted(DSKeyLang.ScreenStorageTemporary.get(bytes(overview.temporaryBytes()))),
                Line.muted(DSKeyLang.ScreenStorageBackups.get(bytes(overview.backupBytes())))
        );
    }

    private static Component indexState(StorageIndexState state) {
        return switch(state) {
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
