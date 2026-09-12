package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.handler.common.ServerLifecycleHandler;
import io.zershyan.damagestats.handler.common.StatsResetService;
import io.zershyan.damagestats.handler.common.StatsSyncHandler;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * GUI 清空操作始终由服务端判定范围与权限，客户端不能借由包字段越权。
 */
public record ResetStatsPacket(boolean global) implements CustomPacketPayload {
    public static final ResetStatsPacket INSTANCE = new ResetStatsPacket(false);
    public static final ResetStatsPacket GLOBAL = new ResetStatsPacket(true);
    public static final Type<ResetStatsPacket> TYPE = new Type<>(DamageStats.id("reset_stats"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResetStatsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ResetStatsPacket::global,
            ResetStatsPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            if(tracker == null) return;
            if(global()) {
                if(!StatsFocusManager.hasFullAccess(player)) return;
                MinecraftServer server = player.getServer();
                if(server == null) return;
                StatsResetService.resetAll(server);
                return;
            }
            EntityRef self = EntityRef.of(player);
            boolean persisted = ServerLifecycleHandler.persistReset(self);
            StatsFocusManager manager = ServerStats.focusManager();
            if(manager != null) {
                if(persisted) manager.resetSummaryCache(player, tracker, self);
                else manager.invalidateSummaryCache(player);
            }
            PacketDistributor.sendToPlayer(player, StatsInvalidatedPacket.INSTANCE);
            StatsSyncHandler.pushFocusState(tracker, player, FocusChangeResult.ACCEPTED);
            if(!persisted) player.displayClientMessage(DSKeyLang.ResetFailed.copy(), false);
        });
    }
}
