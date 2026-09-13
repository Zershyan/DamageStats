package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.view.FocusChartPage;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/** 焦点图表分页响应。 */
public record FocusChartPagePacket(FocusChartPage page) implements CustomPacketPayload {
    public static final Type<FocusChartPagePacket> TYPE = new Type<>(DamageStats.id("focus_chart_page"));
    public static final StreamCodec<FriendlyByteBuf, FocusChartPagePacket> STREAM_CODEC = StreamCodec.composite(
            FocusChartPage.STREAM_CODEC, FocusChartPagePacket::page,
            FocusChartPagePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientStats.acceptChartPage(page()));
    }
}
