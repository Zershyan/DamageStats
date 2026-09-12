package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.stats.view.ExportSection;
import io.zershyan.damagestats.stats.view.GroupView;
import io.zershyan.damagestats.stats.view.SessionView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** 完整导出的固定大小数据块；单块最多包含 50 个分组或会话。 */
public record ExportChunkPacket(
        int requestId,
        int index,
        ExportSection section,
        List<GroupView> groups,
        List<SessionView> history
) implements CustomPacketPayload {
    private static final int CHUNK_SIZE = 50;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<GroupView>> GROUPS_CODEC =
            GroupView.STREAM_CODEC.apply(ByteBufCodecs.list(CHUNK_SIZE));
    private static final StreamCodec<RegistryFriendlyByteBuf, List<SessionView>> HISTORY_CODEC =
            SessionView.STREAM_CODEC.apply(ByteBufCodecs.list(CHUNK_SIZE));
    public static final Type<ExportChunkPacket> TYPE = new Type<>(DamageStats.id("export_chunk"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExportChunkPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ExportChunkPacket::requestId,
            ByteBufCodecs.VAR_INT, ExportChunkPacket::index,
            ExportSection.STREAM_CODEC, ExportChunkPacket::section,
            GROUPS_CODEC, ExportChunkPacket::groups,
            HISTORY_CODEC, ExportChunkPacket::history,
            ExportChunkPacket::new
    );

    public static ExportChunkPacket groups(int requestId, ExportSection section, List<GroupView> groups) {
        return new ExportChunkPacket(requestId, -1, section, List.copyOf(groups), List.of());
    }

    public static ExportChunkPacket history(int requestId, List<SessionView> history) {
        return new ExportChunkPacket(requestId, -1, ExportSection.HISTORY, List.of(), List.copyOf(history));
    }

    public ExportChunkPacket withIndex(int newIndex) {
        return new ExportChunkPacket(requestId, newIndex, section, groups, history);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientExportManager.acceptChunk(this));
    }
}
