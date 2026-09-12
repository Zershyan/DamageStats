package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.stats.view.FocusSummaryDelta;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 仅在焦点数据变化时推送的摘要增量，供 Overlay 与固定页头共用。 */
public record StatsSummaryPacket(FocusSummaryDelta delta) implements CustomPacketPayload {
    public static final Type<StatsSummaryPacket> TYPE = new Type<>(DamageStats.id("stats_summary"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatsSummaryPacket> STREAM_CODEC = StreamCodec.composite(
            FocusSummaryDelta.STREAM_CODEC, StatsSummaryPacket::delta,
            StatsSummaryPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientStats.acceptSummary(delta()));
    }
}
