package io.zershyan.damagestats.handler.common;

import com.mojang.logging.LogUtils;
import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import io.zershyan.damagestats.stats.save.StatsStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.nio.file.Path;

/**
 * 统计数据随服务端生命周期读写。文件在世界目录下，加上单人走的也是集成服务端，
 * 所以换存档时数据自然隔离，不会串到别的存档去。
 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class ServerLifecycleHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 启动时就把目录记下来。崩服兜底那一步 stopServer 已经跑完了，不能再向 server 要路径 */
    private static @Nullable Path worldDirectory;
    private static boolean shutdownSaved;
    private static boolean storageWritesBlocked;
    private static final int JOURNAL_FLUSH_INTERVAL_TICKS = 100;
    private static final int INSTANCE_PRUNE_INTERVAL_TICKS = 1200;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        Path directory = StatsStorage.directory(event.getServer());
        worldDirectory = directory;
        shutdownSaved = false;
        storageWritesBlocked = !StatsStorage.recoverPendingReset(directory);
        if(storageWritesBlocked) {
            LOGGER.error("伤害统计清理事务恢复失败，本次运行不会写入统计数据：{}", worldDirectory);
        }
        DamageEventJournal journal = DamageEventJournal.open(directory);
        // 原始事件日志是权威数据；缓存损坏时回放它，避免实例选择和快速聚合从空数据开始。
        DamageTracker restored = storageWritesBlocked ? null : StatsStorage.load(event.getServer());
        boolean recoveredFromJournal = !storageWritesBlocked && restored == null;
        if(recoveredFromJournal) {
            restored = journal.rebuildTracker(event.getServer().overworld().getGameTime());
            if(!StatsStorage.save(directory, restored)) {
                LOGGER.warn("伤害统计缓存已从事件日志恢复，但修复后的缓存写入失败：{}", directory);
            }
        }
        ServerStats.start(restored, journal, StatsStorage.focusWorldId(directory));
        DamageTracker tracker = ServerStats.tracker();
        if(tracker != null) tracker.pruneInstanceDirectory(System.currentTimeMillis());
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
        storageWritesBlocked = false;
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
        tracker.cacheName(player);
        tracker.touchInstance(player, System.currentTimeMillis());
        manager.focusFor(player);
        StatsSyncHandler.pushFocusState(tracker, player, FocusChangeResult.ACCEPTED);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        long gameTime = event.getServer().overworld().getGameTime();
        if(gameTime % INSTANCE_PRUNE_INTERVAL_TICKS == 0) {
            DamageTracker tracker = ServerStats.tracker();
            if(tracker != null) tracker.pruneInstanceDirectory(System.currentTimeMillis());
        }
        if(gameTime % JOURNAL_FLUSH_INTERVAL_TICKS == 0) {
            DamageEventJournal journal = ServerStats.journal();
            if(!storageWritesBlocked && journal != null && !journal.isClearing() && !journal.flush()) {
                LOGGER.error("定期写出原始伤害事件失败，将在下一次保存或停服时重试");
            }
        }
        int interval = DSConfig.AutoSaveIntervalTicks.get();
        if(interval <= 0 || !DSConfig.AutoSave.get()) return;
        if(gameTime % interval != 0) return;
        flush();
    }

    private static void saveOnShutdown() {
        if(shutdownSaved) return;
        if(storageWritesBlocked) {
            shutdownSaved = true;
            return;
        }
        DamageEventJournal journal = ServerStats.journal();
        if(journal != null && !journal.close()) {
            LOGGER.error("停服时原始伤害事件未能完整刷新或封存，保留状态等待再次尝试");
            return;
        }
        if(!saveStats()) {
            LOGGER.error("停服时伤害统计聚合缓存保存失败，保留状态等待再次尝试");
            return;
        }
        shutdownSaved = true;
    }

    private static boolean flush() {
        if(storageWritesBlocked) return false;
        DamageEventJournal journal = ServerStats.journal();
        if(journal != null && journal.isClearing()) return false;
        if(journal != null && !journal.flush()) return false;
        return saveStats();
    }

    private static boolean saveStats() {
        DamageTracker tracker = ServerStats.tracker();
        DamageEventJournal journal = ServerStats.journal();
        Path directory = worldDirectory;
        if(storageWritesBlocked || tracker == null || directory == null || journal != null && journal.isClearing()) return false;
        return StatsStorage.save(directory, tracker);
    }

    public static void saveNow() {
        flush();
    }

    /** 显式重置不服从自动保存开关，必须让磁盘中的旧统计同步失效。 */
    public static boolean persistReset(@Nullable EntityRef owner) {
        Path directory = worldDirectory;
        if(directory == null || storageWritesBlocked) return false;
        DamageTracker tracker = ServerStats.tracker();
        if(owner == null) {
            DamageEventJournal journal = ServerStats.journal();
            if(journal == null || tracker == null) return false;
            return StatsStorage.resetAllAtomically(directory, journal, tracker);
        } else {
            DamageEventJournal journal = ServerStats.journal();
            if(tracker == null) return false;
            if(journal != null) {
                if(!StatsStorage.invalidateCache(directory)) return false;
                if(!journal.markReset(owner)) return false;
                tracker.resetFor(owner);
                return true;
            }
            tracker.resetFor(owner);
        }
        return flush();
    }

    /** 事务恢复状态不明时禁止采集和保存，直到下次启动成功恢复为止。 */
    public static boolean storageWritesBlocked() {
        return storageWritesBlocked;
    }
}
