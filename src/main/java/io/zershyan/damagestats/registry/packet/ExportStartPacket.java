package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.view.MetricsView;
import io.zershyan.damagestats.stats.view.StatsSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 完整导出的元数据包；分组和历史随后由固定大小的分块传输。 */
public record ExportStartPacket(
        int requestId,
        boolean accepted,
        Component subjectName,
        StatsFilter filter,
        List<Component> filterLabels,
        MetricsView sessionMetrics,
        MetricsView lifetimeMetrics,
        int chunkCount
) implements CustomPacketPayload {
    private static final int MAX_FILTER_LABELS = 8;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> LABELS_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FILTER_LABELS));
    public static final Type<ExportStartPacket> TYPE = new Type<>(DamageStats.id("export_start"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExportStartPacket> STREAM_CODEC =
            StreamCodec.of(ExportStartPacket::encode, ExportStartPacket::decode);

    public static ExportStartPacket accepted(int requestId, StatsSnapshot snapshot, int chunkCount) {
        return new ExportStartPacket(requestId, true, snapshot.subjectName(), snapshot.appliedFilter(),
                List.copyOf(snapshot.filterLabels()), snapshot.session().metrics(), snapshot.lifetime().metrics(), chunkCount);
    }

    public static ExportStartPacket denied(int requestId) {
        return new ExportStartPacket(requestId, false, Component.empty(), StatsFilter.NONE, List.of(),
                MetricsView.EMPTY, MetricsView.EMPTY, 0);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExportStartPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientExportManager.acceptStart(payload));
    }

    private static void encode(RegistryFriendlyByteBuf buf, ExportStartPacket packet) {
        buf.writeVarInt(packet.requestId);
        buf.writeBoolean(packet.accepted);
        ComponentSerialization.STREAM_CODEC.encode(buf, packet.subjectName);
        StatsFilter.STREAM_CODEC.encode(buf, packet.filter);
        LABELS_CODEC.encode(buf, packet.filterLabels);
        MetricsView.STREAM_CODEC.encode(buf, packet.sessionMetrics);
        MetricsView.STREAM_CODEC.encode(buf, packet.lifetimeMetrics);
        buf.writeVarInt(packet.chunkCount);
    }

    private static ExportStartPacket decode(RegistryFriendlyByteBuf buf) {
        return new ExportStartPacket(
                buf.readVarInt(),
                buf.readBoolean(),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                StatsFilter.STREAM_CODEC.decode(buf),
                LABELS_CODEC.decode(buf),
                MetricsView.STREAM_CODEC.decode(buf),
                MetricsView.STREAM_CODEC.decode(buf),
                buf.readVarInt()
        );
    }
}
