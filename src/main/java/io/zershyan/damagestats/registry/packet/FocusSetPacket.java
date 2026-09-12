package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.handler.common.StatsSyncHandler;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** 客户端只提交希望使用的来源和目标，服务端负责验证并返回明确裁决。 */
public record FocusSetPacket(
        Optional<EntitySelector> source,
        Optional<EntitySelector> target,
        boolean sourceIsDirectSource,
        int requestId
) implements CustomPacketPayload {
    public static final Type<FocusSetPacket> TYPE = new Type<>(DamageStats.id("focus_set"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusSetPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(EntitySelector.STREAM_CODEC), FocusSetPacket::source,
            ByteBufCodecs.optional(EntitySelector.STREAM_CODEC), FocusSetPacket::target,
            ByteBufCodecs.BOOL, FocusSetPacket::sourceIsDirectSource,
            ByteBufCodecs.VAR_INT, FocusSetPacket::requestId,
            FocusSetPacket::new
    );

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
            FocusChangeResult result = manager.setFocus(player, source(), target(),
                    sourceIsDirectSource(), tracker);
            StatsSyncHandler.pushFocusState(tracker, player, result, requestId());
        });
    }
}
