package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.view.FocusChartInstancesPage;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/** 焦点图表实例子行页面响应。 */
public record FocusChartInstancesPagePacket(FocusChartInstancesPage page) implements CustomPacketPayload {
    public static final Type<FocusChartInstancesPagePacket> TYPE =
            new Type<>(DamageStats.id("focus_chart_instances_page"));
    public static final StreamCodec<FriendlyByteBuf, FocusChartInstancesPagePacket> STREAM_CODEC =
            StreamCodec.composite(
                    FocusChartInstancesPage.STREAM_CODEC, FocusChartInstancesPagePacket::page,
                    FocusChartInstancesPagePacket::new
            );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientStats.acceptChartInstancesPage(page()));
    }
}
