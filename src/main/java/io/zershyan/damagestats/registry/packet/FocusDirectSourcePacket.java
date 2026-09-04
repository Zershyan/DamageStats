package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.handler.common.StatsSyncHandler;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 请求把当前玩家输出链路中的具体 LivingEntity 直接来源设为焦点来源。 */
public record FocusDirectSourcePacket(EntityRef source) implements CustomPacketPayload {
    public static final Type<FocusDirectSourcePacket> TYPE = new Type<>(DamageStats.id("focus_direct_source"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusDirectSourcePacket> STREAM_CODEC = StreamCodec.composite(
            EntityRef.STREAM_CODEC, FocusDirectSourcePacket::source,
            FocusDirectSourcePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FocusDirectSourcePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            StatsFocusManager manager = ServerStats.focusManager();
            if(tracker == null || manager == null) return;
            FocusChangeResult result = manager.selectDirectSource(player, payload.source(), tracker);
            StatsSyncHandler.pushFocusState(tracker, player, result);
        });
    }
}
