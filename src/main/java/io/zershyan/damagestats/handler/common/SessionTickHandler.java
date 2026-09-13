package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** 驱动战斗会话的超时判定 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class SessionTickHandler {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return;
        // gameTime 全局单调递增，各维度共享，可以直接当统一时间轴用
        long gameTime = event.getServer().overworld().getGameTime();
        // 每 tick 检查以确保会话超时状态瞬间同步
        tracker.tick(gameTime);
        StatsSyncHandler.onSessionTimeoutCheck(gameTime);
    }
}
