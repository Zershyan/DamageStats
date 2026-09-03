package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.stats.view.OverlaySummaryDelta;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 服务端每秒推一次的精简数据，只够 Overlay 用，所以包很小 */
public record StatsSummaryPacket(OverlaySummaryDelta delta) implements CustomPacketPayload {
    public static final Type<StatsSummaryPacket> TYPE = new Type<>(DamageStats.id("stats_summary"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatsSummaryPacket> STREAM_CODEC = StreamCodec.composite(
            OverlaySummaryDelta.STREAM_CODEC, StatsSummaryPacket::delta,
            StatsSummaryPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StatsSummaryPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientStats.acceptSummary(payload.delta()));
    }
}
