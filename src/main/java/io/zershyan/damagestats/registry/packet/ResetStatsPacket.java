package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.handler.common.ServerLifecycleHandler;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家从界面上清空自己的统计。只动他本人的条目，所以不需要权限；
 * 指令里的 reset 清的是全局数据，那个要权限。
 */
public record ResetStatsPacket() implements CustomPacketPayload {
    public static final ResetStatsPacket INSTANCE = new ResetStatsPacket();
    public static final Type<ResetStatsPacket> TYPE = new Type<>(DamageStats.id("reset_stats"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResetStatsPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ResetStatsPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            if(tracker == null) return;
            EntityRef self = EntityRef.of(player);
            tracker.resetFor(self);
            ServerLifecycleHandler.persistReset(self);
            PacketDistributor.sendToPlayer(player, StatsInvalidatedPacket.INSTANCE);
        });
    }
}
