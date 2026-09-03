package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.ServerStats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * 统计数据随服务端生命周期创建和销毁。单人游戏走的是集成服务端，同样会触发这两个事件，
 * 所以换存档时数据自然隔离，不会串到别的存档去。
 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class ServerLifecycleHandler {
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ServerStats.start();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerStats.stop();
    }

    private ServerLifecycleHandler() {
    }
}
