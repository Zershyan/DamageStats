package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.registry.DSPackets;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.filter.StatsSubjectSlot;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.view.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** 客户端请求当前浏览条件的完整导出，服务端授权后分块返回所有分组和历史。 */
public record ExportRequestPacket(StatsFilter filter, DamageTypeGrouping typeGrouping, int requestId) implements CustomPacketPayload {
    private static final int CHUNK_SIZE = 50;
    public static final Type<ExportRequestPacket> TYPE = new Type<>(DamageStats.id("export_request"));
    public static final StreamCodec<FriendlyByteBuf, ExportRequestPacket> STREAM_CODEC = StreamCodec.composite(
            StatsFilter.STREAM_CODEC, ExportRequestPacket::filter,
            DamageTypeGrouping.STREAM_CODEC, ExportRequestPacket::typeGrouping,
            ByteBufCodecs.VAR_INT, ExportRequestPacket::requestId,
            ExportRequestPacket::new
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
                DSPackets.sendToPlayer(player, ExportStartPacket.denied(requestId()));
                return;
            }
            StatsSnapshot snapshot = StatsViewBuilder.snapshotFor(tracker, player, filter(),
                    StatsSubjectSlot.SOURCE.resolve(filter()),
                    typeGrouping());
            List<ExportChunkPacket> chunks = chunks(requestId(), snapshot);
            DSPackets.sendToPlayer(player, ExportStartPacket.accepted(requestId(), snapshot, chunks.size()));
            chunks.forEach(chunk -> DSPackets.sendToPlayer(player, chunk));
        });
    }

    private static List<ExportChunkPacket> chunks(int requestId, StatsSnapshot snapshot) {
        List<ExportChunkPacket> chunks = new ArrayList<>();
        appendGroups(chunks, requestId, ExportSection.SESSION_DAMAGE_TYPE, snapshot.session().byType());
        appendGroups(chunks, requestId, ExportSection.SESSION_DIRECT_SOURCE, snapshot.session().bySource());
        appendGroups(chunks, requestId, ExportSection.SESSION_OPPONENT, snapshot.session().byOpponent());
        appendGroups(chunks, requestId, ExportSection.LIFETIME_DAMAGE_TYPE, snapshot.lifetime().byType());
        appendGroups(chunks, requestId, ExportSection.LIFETIME_DIRECT_SOURCE, snapshot.lifetime().bySource());
        appendGroups(chunks, requestId, ExportSection.LIFETIME_OPPONENT, snapshot.lifetime().byOpponent());
        appendHistory(chunks, requestId, snapshot.history());
        for (int index = 0; index < chunks.size(); index++) chunks.set(index, chunks.get(index).withIndex(index));
        return List.copyOf(chunks);
    }

    private static void appendGroups(List<ExportChunkPacket> chunks, int requestId,
                                     ExportSection section, List<GroupView> groups) {
        for (int start = 0; start < groups.size(); start += CHUNK_SIZE) {
            int end = Math.min(groups.size(), start + CHUNK_SIZE);
            chunks.add(ExportChunkPacket.groups(requestId, section, groups.subList(start, end)));
        }
    }

    private static void appendHistory(List<ExportChunkPacket> chunks, int requestId, List<SessionView> history) {
        for (int start = 0; start < history.size(); start += CHUNK_SIZE) {
            int end = Math.min(history.size(), start + CHUNK_SIZE);
            chunks.add(ExportChunkPacket.history(requestId, history.subList(start, end)));
        }
    }
}
