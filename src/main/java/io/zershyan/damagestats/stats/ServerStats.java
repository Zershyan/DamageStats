package io.zershyan.damagestats.stats;

import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import org.jetbrains.annotations.Nullable;

/**
 * 服务端统计数据的入口。整个存档共用一份 {@link DamageTracker}，
 * 因为玩家会跨维度作战，按维度分开会把一场战斗切成几段。
 */
public final class ServerStats {
    private static @Nullable DamageTracker tracker;
    private static @Nullable DamageEventJournal journal;
    private static @Nullable StatsFocusManager focusManager;
    private static String worldId = "";

    public static @Nullable DamageTracker tracker() {
        return tracker;
    }

    /** 传入从存档读回的数据；传 null 表示这个存档还没有统计，从空的开始 */
    public static void start(@Nullable DamageTracker restored, DamageEventJournal eventJournal, String currentWorldId) {
        tracker = restored != null ? restored : new DamageTracker();
        journal = eventJournal;
        focusManager = new StatsFocusManager();
        worldId = currentWorldId;
    }

    public static @Nullable DamageEventJournal journal() {
        return journal;
    }

    public static @Nullable StatsFocusManager focusManager() {
        return focusManager;
    }

    public static String worldId() {
        return worldId;
    }

    public static void stop() {
        tracker = null;
        journal = null;
        focusManager = null;
        worldId = "";
    }
}
