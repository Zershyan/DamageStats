package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.view.HistoryPage;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/** 历史会话的按需响应，客户端用请求编号丢弃过期结果。 */
public record HistoryPagePacket(HistoryPage page) implements CustomPacketPayload {
    public static final Type<HistoryPagePacket> TYPE = new Type<>(DamageStats.id("history_page"));
    public static final StreamCodec<FriendlyByteBuf, HistoryPagePacket> STREAM_CODEC = StreamCodec.composite(
            HistoryPage.STREAM_CODEC, HistoryPagePacket::page,
            HistoryPagePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientStats.acceptHistoryPage(page()));
    }
}
