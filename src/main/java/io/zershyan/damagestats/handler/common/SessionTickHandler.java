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
    /** 超时阈值是秒级的，没必要每 tick 遍历一遍所有条目 */
    private static final int CHECK_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return;
        // gameTime 全局单调递增，各维度共享，可以直接当统一时间轴用
        long gameTime = event.getServer().overworld().getGameTime();
        if(gameTime % CHECK_INTERVAL_TICKS != 0) return;
        tracker.tick(gameTime);
    }
}
