package io.zershyan.damagestats.stats;

import org.jetbrains.annotations.Nullable;

/**
 * 服务端统计数据的入口。整个存档共用一份 {@link DamageTracker}，
 * 因为玩家会跨维度作战，按维度分开会把一场战斗切成几段。
 */
public final class ServerStats {
    private static @Nullable DamageTracker tracker;

    public static @Nullable DamageTracker tracker() {
        return tracker;
    }

    public static void start() {
        tracker = new DamageTracker();
    }

    public static void stop() {
        tracker = null;
    }

    private ServerStats() {
    }
}
