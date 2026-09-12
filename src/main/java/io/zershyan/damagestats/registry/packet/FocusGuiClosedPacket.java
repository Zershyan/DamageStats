package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.handler.common.StatsSyncHandler;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** GUI 关闭后回收普通玩家的实体来源下钻授权。 */
public record FocusGuiClosedPacket() implements CustomPacketPayload {
    public static final FocusGuiClosedPacket INSTANCE = new FocusGuiClosedPacket();
    public static final Type<FocusGuiClosedPacket> TYPE = new Type<>(DamageStats.id("focus_gui_closed"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusGuiClosedPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            StatsFocusManager manager = ServerStats.focusManager();
            if(tracker == null || manager == null || !manager.closeGui(player)) return;
            StatsSyncHandler.pushFocusState(tracker, player, FocusChangeResult.ACCEPTED);
        });
    }
}
