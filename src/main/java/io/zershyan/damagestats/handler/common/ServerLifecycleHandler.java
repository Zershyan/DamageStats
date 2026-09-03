package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.save.StatsStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * 统计数据随服务端生命周期读写。单人游戏走的是集成服务端，同样会触发这些事件，
 * 加上文件本来就放在世界目录下，所以换存档时数据自然隔离，不会串到别的存档去。
 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class ServerLifecycleHandler {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerStats.start(DSConfig.AutoSave.get() ? StatsStorage.load(event.getServer()) : null);
    }

    /** 写盘放在 Stopping 而不是 Stopped：这时世界目录还没被释放 */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if(!DSConfig.AutoSave.get()) return;
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return;
        StatsStorage.save(event.getServer(), tracker);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerStats.stop();
    }
}
