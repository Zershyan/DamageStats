package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.StatsSummaryPacket;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.StatsEntry;
import io.zershyan.damagestats.stats.view.OverlaySummary;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 每秒给每个在线玩家推一份 Overlay 用的精简数据。不推完整统计——那个体量只在玩家打开 GUI 时按需发。
 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class StatsSyncHandler {
    private static final int PUSH_INTERVAL_TICKS = 20;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return;
        long gameTime = event.getServer().overworld().getGameTime();
        if(gameTime % PUSH_INTERVAL_TICKS != 0) return;
        int window = DSConfig.DpsWindowTicks.get();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player,
                    new StatsSummaryPacket(summaryFor(tracker, player, gameTime, window)));
        }
    }

    private static OverlaySummary summaryFor(DamageTracker tracker, ServerPlayer player, long gameTime, int window) {
        StatsEntry entry = tracker.outgoing(EntityRef.of(player));
        EntityRef target = tracker.overlayTarget(player.getUUID());
        // 没锁定也没交战过，就展示玩家的总输出
        if(target == null) return StatsViewBuilder.overlay(
                DSKeyLang.OverlayAllTargets.copy(), entry, gameTime, window);
        return StatsViewBuilder.overlayForOpponent(
                StatsNames.opponent(tracker, target), entry, target, gameTime, window);
    }
}
