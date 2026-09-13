package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.registry.DSPackets;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.view.HistoryPage;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 历史会话使用当前浏览筛选，并由服务端重新执行权限校验。 */
public record HistoryRequestPacket(StatsFilter filter, int requestId) implements CustomPacketPayload {
    public static final Type<HistoryRequestPacket> TYPE = new Type<>(DamageStats.id("history_request"));
    public static final StreamCodec<FriendlyByteBuf, HistoryRequestPacket> STREAM_CODEC = StreamCodec.composite(
            StatsFilter.STREAM_CODEC, HistoryRequestPacket::filter,
            ByteBufCodecs.VAR_INT, HistoryRequestPacket::requestId,
            HistoryRequestPacket::new
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
            if(!manager.canBrowse(player, filter(), tracker)) {
                DSPackets.sendToPlayer(player, new HistoryPagePacket(
                        new HistoryPage(requestId(), false, List.of(), List.of())));
                return;
            }
            List<io.zershyan.damagestats.stats.view.SessionView> history =
                    StatsViewBuilder.historyFromJournal(filter(), player.level().getGameTime());
            boolean incoming = filter().source().isEmpty() && filter().target().isPresent();
            DSPackets.sendToPlayer(player, new HistoryPagePacket(new HistoryPage(requestId(), true,
                    incoming ? List.of() : history, incoming ? history : List.of())));
        });
    }
}
