package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.handler.common.StatsSyncHandler;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/** GUI 打开时请求一份从服务端权威数据重建的完整焦点摘要。 */
public record FocusStateRequestPacket(int requestId) implements CustomPacketPayload {
    public static final Type<FocusStateRequestPacket> TYPE = new Type<>(DamageStats.id("focus_state_request"));
    public static final StreamCodec<FriendlyByteBuf, FocusStateRequestPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, FocusStateRequestPacket::requestId,
                    FocusStateRequestPacket::new);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            StatsFocusManager manager = ServerStats.focusManager();
            if(tracker == null || manager == null) return;
            manager.refreshSummary(tracker, player);
            StatsSyncHandler.pushFocusState(tracker, player, FocusChangeResult.ACCEPTED, requestId());
        });
    }
}
