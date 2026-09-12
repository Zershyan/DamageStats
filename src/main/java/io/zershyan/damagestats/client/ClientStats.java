package io.zershyan.damagestats.client;

import io.zershyan.damagestats.stats.filter.FilterKey;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.view.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端侧的数据镜像。只存服务端推过来的视图，不自行计算任何权威数据，
 * Overlay 与页头读取焦点摘要；图表、实例候选与历史页只在用户操作时按需请求。
 */
public final class ClientStats {
    private static FocusSummary summary = FocusSummary.empty();
    private static FocusChangeResult lastFocusResult = FocusChangeResult.ACCEPTED;
    private static @Nullable EntityChoicePage entityChoices;
    private static @Nullable FocusChartPage chartPage;
    private static final Map<FilterKey, FocusChartInstancesPage> chartInstancesPages = new HashMap<>();
    private static final Map<FilterKey, Integer> chartInstancesRequestIds = new HashMap<>();
    private static @Nullable HistoryPage historyPage;
    private static boolean snapshotInvalidated;
    private static boolean focusStateReceived;
    private static int entityChoiceRequestId;
    private static int chartRequestId;
    private static int historyRequestId;

    public static void acceptSummary(FocusSummaryDelta delta) {
        resetIfWorldChanged(delta.summary().worldId());
        if(delta.summary().revision() <= summary.revision()) return;
        summary = delta.applyTo(summary);
    }

    public static void acceptFocusState(FocusChangeResult result, FocusSummary incoming, int requestId) {
        resetIfWorldChanged(incoming.worldId());
        if(incoming.revision() < summary.revision()) return;
        if(summary.scope().version() != incoming.scope().version()) {
            chartPage = null;
            clearChartInstances();
        }
        summary = incoming;
        focusStateReceived = true;
        lastFocusResult = result;
        ClientFocusPreferences.acceptFocusState(result, incoming, requestId);
    }

    public static FocusSummary summary() {
        return summary;
    }

    public static FocusChangeResult lastFocusResult() {
        return lastFocusResult;
    }

    public static boolean hasFocusState() {
        return focusStateReceived;
    }

    /** 打开统计页前清除旧回执标记，使页面只在本次权威刷新完成后开始请求图表。 */
    public static void expectFocusState() {
        focusStateReceived = false;
    }

    public static void acceptEntityChoices(EntityChoicePage page) {
        if(page.requestId() < entityChoiceRequestId) return;
        entityChoices = page;
    }

    public static @Nullable EntityChoicePage entityChoices() {
        return entityChoices;
    }

    public static void acceptChartPage(FocusChartPage page) {
        if(page.requestId() < chartRequestId) return;
        chartPage = page;
    }

    public static void acceptChartInstancesPage(FocusChartInstancesPage page) {
        int requestId = chartInstancesRequestIds.getOrDefault(page.parentKey(), 0);
        if(page.requestId() < requestId) return;
        FocusChartInstancesPage previous = chartInstancesPages.get(page.parentKey());
        if(previous != null && previous.allowed() && page.allowed()
                && previous.focusVersion() == page.focusVersion()
                && previous.snapshotId() == page.snapshotId()
                && previous.dimension() == page.dimension()
                && previous.scope() == page.scope()
                && previous.typeGrouping() == page.typeGrouping()
                && previous.filter().equals(page.filter())
                && previous.nextCursor().equals(page.cursor())) {
            List<io.zershyan.damagestats.stats.view.GroupView> rows = new ArrayList<>(previous.rows());
            rows.addAll(page.rows());
            page = new FocusChartInstancesPage(page.focusVersion(), page.requestId(), page.snapshotId(),
                    previous.cursor(), page.nextCursor(), page.allowed(), page.dimension(), page.scope(),
                    page.typeGrouping(), page.filter(), page.parentKey(), List.copyOf(rows), page.hasNext());
        }
        chartInstancesPages.put(page.parentKey(), page);
    }

    public static int nextChartInstancesRequestId(FilterKey parentKey) {
        int requestId = chartInstancesRequestIds.getOrDefault(parentKey, 0) + 1;
        chartInstancesRequestIds.put(parentKey, requestId);
        return requestId;
    }

    public static @Nullable FocusChartInstancesPage chartInstancesPage(FilterKey parentKey) {
        return chartInstancesPages.get(parentKey);
    }

    public static void clearChartInstances() {
        chartInstancesPages.clear();
        chartInstancesRequestIds.replaceAll((key, requestId) -> requestId + 1);
    }

    private static void resetChartInstances() {
        clearChartInstances();
        chartInstancesRequestIds.clear();
    }

    public static int nextEntityChoiceRequestId() {
        return ++entityChoiceRequestId;
    }

    public static int nextChartRequestId() {
        return ++chartRequestId;
    }

    public static void acceptHistoryPage(HistoryPage page) {
        if(page.requestId() < historyRequestId) return;
        historyPage = page;
    }

    public static int nextHistoryRequestId() {
        return ++historyRequestId;
    }

    public static @Nullable FocusChartPage chartPage() {
        return chartPage;
    }

    public static @Nullable HistoryPage historyPage() {
        return historyPage;
    }

    public static void invalidateSnapshot() {
        summary = FocusSummary.empty();
        focusStateReceived = false;
        entityChoices = null;
        chartPage = null;
        resetChartInstances();
        historyPage = null;
        snapshotInvalidated = true;
    }

    public static boolean consumeSnapshotInvalidation() {
        boolean invalidated = snapshotInvalidated;
        snapshotInvalidated = false;
        return invalidated;
    }

    /** 退出世界时清掉，否则进下一个存档会看到上一局的残留 */
    public static void clear() {
        ClientExportManager.clear();
        ClientStorageOverview.clear();
        summary = FocusSummary.empty();
        focusStateReceived = false;
        lastFocusResult = FocusChangeResult.ACCEPTED;
        entityChoices = null;
        chartPage = null;
        resetChartInstances();
        historyPage = null;
        snapshotInvalidated = false;
    }

    private static void resetIfWorldChanged(String worldId) {
        if(worldId.isEmpty() || summary.worldId().isEmpty() || worldId.equals(summary.worldId())) return;
        summary = FocusSummary.empty();
        focusStateReceived = false;
        entityChoices = null;
        chartPage = null;
        resetChartInstances();
        historyPage = null;
        snapshotInvalidated = false;
    }
}
