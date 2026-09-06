package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import io.zershyan.damagestats.stats.save.StatsStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * 统计数据随服务端生命周期读写。文件在世界目录下，加上单人走的也是集成服务端，
 * 所以换存档时数据自然隔离，不会串到别的存档去。
 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class ServerLifecycleHandler {
    /** 启动时就把目录记下来。崩服兜底那一步 stopServer 已经跑完了，不能再向 server 要路径 */
    private static @Nullable Path worldDirectory;
    private static boolean shutdownSaved;
    private static final int JOURNAL_FLUSH_INTERVAL_TICKS = 100;
    private static final int INSTANCE_PRUNE_INTERVAL_TICKS = 1200;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        worldDirectory = StatsStorage.directory(event.getServer());
        shutdownSaved = false;
        // 原始事件日志是权威数据；此缓存仍保存实例目录、名称和快速聚合，不能因关闭定时快照而丢失。
        DamageTracker restored = StatsStorage.load(event.getServer());
        ServerStats.start(restored, DamageEventJournal.open(worldDirectory), StatsStorage.focusWorldId(worldDirectory));
        ServerStats.tracker().pruneInstanceDirectory(System.currentTimeMillis());
    }

    /** 正常退出走这里，这时存档会话还开着 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        saveOnShutdown();
    }

    /**
     * 崩服时 MinecraftServer 会跳过 Stopping 直接进 finally，所以这里兜一道底。
     * 真正救不回来的只剩被强制结束进程（kill -9、断电），那种情况靠 autoSaveIntervalTicks。
     */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        saveOnShutdown();
        ServerStats.stop();
        StatsSyncHandler.clear();
        worldDirectory = null;
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        StatsFocusManager manager = ServerStats.focusManager();
        if(manager != null) manager.remove(event.getEntity().getUUID());
        StatsSyncHandler.clearPlayer(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return;
        DamageTracker tracker = ServerStats.tracker();
        StatsFocusManager manager = ServerStats.focusManager();
        if(tracker == null || manager == null) return;
        manager.focusFor(player);
        StatsSyncHandler.pushFocusState(tracker, player, FocusChangeResult.ACCEPTED);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        long gameTime = event.getServer().overworld().getGameTime();
        if(gameTime % INSTANCE_PRUNE_INTERVAL_TICKS == 0) {
            DamageTracker tracker = ServerStats.tracker();
            if(tracker != null) tracker.pruneInstanceDirectory(System.currentTimeMillis());
        }
        if(gameTime % JOURNAL_FLUSH_INTERVAL_TICKS == 0) {
            DamageEventJournal journal = ServerStats.journal();
            if(journal != null) journal.flush();
        }
        int interval = DSConfig.AutoSaveIntervalTicks.get();
        if(interval <= 0 || !DSConfig.AutoSave.get()) return;
        if(gameTime % interval != 0) return;
        flush();
    }

    private static void saveOnShutdown() {
        if(shutdownSaved) return;
        DamageEventJournal journal = ServerStats.journal();
        if(journal != null) journal.close();
        saveStats();
        shutdownSaved = true;
    }

    private static boolean flush() {
        DamageEventJournal journal = ServerStats.journal();
        if(journal != null) journal.flush();
        return saveStats();
    }

    private static boolean saveStats() {
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null || worldDirectory == null) return false;
        return StatsStorage.save(worldDirectory, tracker);
    }

    public static void saveNow() {
        flush();
    }

    /** 显式重置不服从自动保存开关，必须让磁盘中的旧统计同步失效。 */
    public static boolean persistReset(@Nullable EntityRef owner) {
        if(worldDirectory == null) return false;
        DamageTracker tracker = ServerStats.tracker();
        if(owner == null) {
            DamageEventJournal journal = ServerStats.journal();
            if(journal != null && !journal.clear()) return false;
            if(tracker != null) tracker.reset();
        } else {
            DamageEventJournal journal = ServerStats.journal();
            if(journal != null && !journal.markReset(owner)) return false;
            if(tracker != null) tracker.resetFor(owner);
        }
        return flush();
    }
}
