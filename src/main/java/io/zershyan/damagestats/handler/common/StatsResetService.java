package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.registry.packet.StatsInvalidatedPacket;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.network.PacketDistributor;

/** 全局重置必须同时失效事件、聚合与客户端镜像，避免清理后仍可见旧统计。 */
public final class StatsResetService {
    private StatsResetService() {}

    public static boolean resetAll(MinecraftServer server) {
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return false;
        if(!ServerLifecycleHandler.persistReset(null)) return false;
        StatsFocusManager manager = ServerStats.focusManager();
        if(manager != null) manager.invalidateSummaryCaches(server.getPlayerList().getPlayers());
        StatsSyncHandler.clear();
        PacketDistributor.sendToAllPlayers(StatsInvalidatedPacket.INSTANCE);
        server.getPlayerList().getPlayers().forEach(online ->
                StatsSyncHandler.pushFocusState(tracker, online, FocusChangeResult.ACCEPTED));
        return true;
    }
}
