package io.zershyan.damagestats.handler.client;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientFocusPreferences;
import io.zershyan.damagestats.client.ClientStats;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** 处理客户端登出清理和客户端 Tick。 */
@EventBusSubscriber(modid = DamageStats.MODID, value = Dist.CLIENT)
public final class ClientLifecycleHandler {
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientStats.clear();
        ClientFocusPreferences.clearSession();
        ClientInputHandler.clearCachedScreen();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        ClientFocusPreferences.applyIfReady();
    }
}
