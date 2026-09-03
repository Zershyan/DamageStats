package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.StatsEntry;
import io.zershyan.damagestats.stats.view.StatsSnapshot;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 客户端打开 GUI 时请求一次完整数据，避免把大包塞进每秒的推送里 */
public record StatsRequestPacket() implements CustomPacketPayload {
    public static final StatsRequestPacket INSTANCE = new StatsRequestPacket();
    public static final Type<StatsRequestPacket> TYPE = new Type<>(DamageStats.id("stats_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatsRequestPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StatsRequestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            if(tracker == null) return;
            PacketDistributor.sendToPlayer(player, new StatsSnapshotPacket(snapshotFor(tracker, player)));
        });
    }

    private static StatsSnapshot snapshotFor(DamageTracker tracker, ServerPlayer player) {
        EntityRef self = EntityRef.of(player);
        long gameTime = player.level().getGameTime();
        int window = DSConfig.DpsWindowTicks.get();
        StatsEntry out = tracker.outgoing(self);
        StatsEntry in = tracker.incoming(self);
        Component name = player.getDisplayName().copy();
        return new StatsSnapshot(
                StatsViewBuilder.entry(tracker, name, out, gameTime, window),
                StatsViewBuilder.entry(tracker, name, in, gameTime, window),
                out == null ? List.of() : StatsViewBuilder.history(out));
    }
}
