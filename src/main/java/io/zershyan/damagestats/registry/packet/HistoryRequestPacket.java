package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
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

import java.util.List;

/** 历史会话使用当前浏览筛选，并由服务端重新执行权限校验。 */
public record HistoryRequestPacket(StatsFilter filter, int requestId) implements CustomPacketPayload {
    public static final Type<HistoryRequestPacket> TYPE = new Type<>(DamageStats.id("history_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HistoryRequestPacket> STREAM_CODEC = StreamCodec.composite(
            StatsFilter.STREAM_CODEC, HistoryRequestPacket::filter,
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
            StatsFocusManager manager = ServerStats.focusManager();
            if(tracker == null || manager == null) return;
            if(!manager.canBrowse(player, payload.filter(), tracker)) {
                PacketDistributor.sendToPlayer(player, new HistoryPagePacket(
                        new HistoryPage(payload.requestId(), false, List.of(), List.of())));
                return;
            }
            List<io.zershyan.damagestats.stats.view.SessionView> history =
                    StatsViewBuilder.historyFromJournal(payload.filter(), player.level().getGameTime());
            boolean incoming = payload.filter().source().isEmpty() && payload.filter().target().isPresent();
            PacketDistributor.sendToPlayer(player, new HistoryPagePacket(new HistoryPage(payload.requestId(), true,
                    incoming ? List.of() : history, incoming ? history : List.of())));
        });
    }
}
