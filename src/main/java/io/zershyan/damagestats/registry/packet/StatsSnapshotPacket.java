package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.stats.view.StatsSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** GUI 打开时请求到的完整数据，只在请求时发一次 */
public record StatsSnapshotPacket(StatsSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<StatsSnapshotPacket> TYPE = new Type<>(DamageStats.id("stats_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StatsSnapshotPacket> STREAM_CODEC = StreamCodec.composite(
            StatsSnapshot.STREAM_CODEC, StatsSnapshotPacket::snapshot,
            StatsSnapshotPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StatsSnapshotPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientStats.acceptSnapshot(payload.snapshot()));
    }
}
