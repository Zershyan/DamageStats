package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
/** 服务端状态发生变化后通知客户端丢弃旧快照并重新请求当前筛选条件 */
public record StatsInvalidatedPacket() implements CustomPacketPayload {
    public static final StatsInvalidatedPacket INSTANCE = new StatsInvalidatedPacket();
    public static final Type<StatsInvalidatedPacket> TYPE = new Type<>(DamageStats.id("stats_invalidated"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatsInvalidatedPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(ClientStats::invalidateSnapshot);
    }
}
