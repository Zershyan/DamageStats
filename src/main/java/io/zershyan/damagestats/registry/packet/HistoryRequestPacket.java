package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.view.HistoryPage;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** 历史会话仅允许读取请求者自身的造成与承受摘要。 */
public record HistoryRequestPacket(int requestId) implements CustomPacketPayload {
    public static final Type<HistoryRequestPacket> TYPE = new Type<>(DamageStats.id("history_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HistoryRequestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HistoryRequestPacket::requestId,
            HistoryRequestPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(HistoryRequestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            if(tracker == null) return;
            EntityRef self = EntityRef.of(player);
            PacketDistributor.sendToPlayer(player, new HistoryPagePacket(new HistoryPage(payload.requestId(),
                    StatsViewBuilder.historyFromJournal(StatsFilter.fromSource(new EntitySelector.Instance(self)),
                            player.level().getGameTime()),
                    StatsViewBuilder.historyFromJournal(new StatsFilter(Optional.empty(),
                            Optional.of(new EntitySelector.Instance(self)), Optional.empty(), Optional.empty()),
                            player.level().getGameTime()))));
        });
    }
}
