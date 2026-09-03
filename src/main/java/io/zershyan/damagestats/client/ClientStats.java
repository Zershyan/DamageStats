package io.zershyan.damagestats.client;

import io.zershyan.damagestats.stats.view.OverlaySummary;
import io.zershyan.damagestats.stats.view.OverlaySummaryDelta;
import io.zershyan.damagestats.stats.view.StatsSnapshot;
import org.jetbrains.annotations.Nullable;

/**
 * 客户端侧的数据镜像。只存服务端推过来的视图，不自行计算任何权威数据，
 * Overlay 读 summary（每秒推一次），GUI 读 snapshot（打开或改筛选时请求一次）。
 */
public final class ClientStats {
    private static OverlaySummary summary = OverlaySummary.empty();
    private static @Nullable StatsSnapshot snapshot;
    private static boolean snapshotInvalidated;

    public static void acceptSummary(OverlaySummaryDelta delta) {
        summary = delta.applyTo(summary);
    }

    public static void acceptSnapshot(StatsSnapshot incoming) {
        snapshot = incoming;
        snapshotInvalidated = false;
    }

    public static OverlaySummary summary() {
        return summary;
    }

    public static @Nullable StatsSnapshot snapshot() {
        return snapshot;
    }

    public static void invalidateSnapshot() {
        summary = OverlaySummary.empty();
        snapshot = null;
        snapshotInvalidated = true;
    }

    public static boolean consumeSnapshotInvalidation() {
        boolean invalidated = snapshotInvalidated;
        snapshotInvalidated = false;
        return invalidated;
    }

    /** 退出世界时清掉，否则进下一个存档会看到上一局的残留 */
    public static void clear() {
        summary = OverlaySummary.empty();
        snapshot = null;
        snapshotInvalidated = false;
    }
}
