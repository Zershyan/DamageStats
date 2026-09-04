package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.registry.packet.FocusStatePacket;
import io.zershyan.damagestats.registry.packet.StatsSummaryPacket;
import io.zershyan.damagestats.stats.DamageRecord;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.view.FocusSummary;
import io.zershyan.damagestats.stats.view.FocusSummaryDelta;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/** 仅同步受伤害影响的焦点；同 tick 的多次命中压缩为一份摘要增量。 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class StatsSyncHandler {
    private static final Map<UUID, FocusSummary> LAST_SUMMARIES = new HashMap<>();
    private static final Set<UUID> DIRTY_PLAYERS = new HashSet<>();

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        DamageTracker tracker = ServerStats.tracker();
        StatsFocusManager manager = ServerStats.focusManager();
        if(tracker == null || manager == null || DIRTY_PLAYERS.isEmpty()) return;
        Map<UUID, ServerPlayer> online = new HashMap<>();
        event.getServer().getPlayerList().getPlayers().forEach(player -> online.put(player.getUUID(), player));
        Set<UUID> dirty = Set.copyOf(DIRTY_PLAYERS);
        DIRTY_PLAYERS.removeAll(dirty);
        dirty.forEach(playerId -> {
            ServerPlayer player = online.get(playerId);
            if(player != null) pushTo(tracker, manager, player);
        });
    }

    public static void onDamageRecorded(DamageRecord record) {
        StatsFocusManager manager = ServerStats.focusManager();
        if(manager == null) return;
        DIRTY_PLAYERS.addAll(manager.matchingSubscribers(record));
    }

    /** 只在会话检查点推送真正超时的焦点，不会为了同步而固定轮询所有玩家。 */
    public static void onSessionTimeoutCheck(long gameTime) {
        StatsFocusManager manager = ServerStats.focusManager();
        if(manager == null) return;
        DIRTY_PLAYERS.addAll(manager.expireSessions(gameTime));
    }

    public static void pushFocusState(DamageTracker tracker, ServerPlayer player, FocusChangeResult result) {
        pushFocusState(tracker, player, result, 0);
    }

    public static void pushFocusState(DamageTracker tracker, ServerPlayer player, FocusChangeResult result, int requestId) {
        StatsFocusManager manager = ServerStats.focusManager();
        if(manager == null) return;
        UUID playerId = player.getUUID();
        FocusSummary summary = revised(playerId, manager.summaryFor(tracker, player));
        LAST_SUMMARIES.put(playerId, summary);
        DIRTY_PLAYERS.remove(playerId);
        PacketDistributor.sendToPlayer(player, new FocusStatePacket(result, summary, requestId));
    }

    private static void pushTo(DamageTracker tracker, StatsFocusManager manager, ServerPlayer player) {
        UUID playerId = player.getUUID();
        FocusSummary summary = revised(playerId, manager.summaryFor(tracker, player));
        FocusSummaryDelta delta = FocusSummaryDelta.between(LAST_SUMMARIES.put(playerId, summary), summary);
        if(delta.isEmpty()) return;
        PacketDistributor.sendToPlayer(player, new StatsSummaryPacket(delta));
    }

    public static void clearPlayer(UUID playerId) {
        LAST_SUMMARIES.remove(playerId);
        DIRTY_PLAYERS.remove(playerId);
    }

    public static void clear() {
        LAST_SUMMARIES.clear();
        DIRTY_PLAYERS.clear();
    }

    private static FocusSummary revised(UUID playerId, FocusSummary summary) {
        FocusSummary previous = LAST_SUMMARIES.get(playerId);
        long revision = previous == null ? 1 : previous.revision() + 1;
        return summary.withRevision(revision);
    }
}
