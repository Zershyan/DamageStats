package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** 驱动战斗会话的超时判定 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class SessionTickHandler {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return;
        // gameTime 全局单调递增，各维度共享，可以直接当统一时间轴用
        long gameTime = event.getServer().overworld().getGameTime();
        // 每 tick 检查以确保会话超时状态瞬间同步
        tracker.tick(gameTime);
        StatsSyncHandler.onSessionTimeoutCheck(gameTime);
    }
}
