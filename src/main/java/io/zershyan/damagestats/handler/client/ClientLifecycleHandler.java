package io.zershyan.damagestats.handler.client;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.overlay.StatsOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** 注册常显浮层，并在退出世界时清掉客户端缓存，免得进下一个存档看到上一局的残留 */
@EventBusSubscriber(modid = DamageStats.MODID, value = Dist.CLIENT)
public final class ClientLifecycleHandler {
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(DamageStats.id("stats_overlay"), StatsOverlay::render);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStats.clear();
    }
}
