package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStorageOverview;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.save.StorageCleanupTarget;
import io.zershyan.damagestats.stats.save.StorageOverview;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/** 服务端存储概览与清理完成状态，命令入口可要求客户端直接打开概览页。 */
public record StorageOverviewPacket(
        boolean allowed,
        boolean openScreen,
        StorageCleanupTarget completedCleanup,
        boolean cleanupSucceeded,
        StorageOverview overview
) implements CustomPacketPayload {
    public static final Type<StorageOverviewPacket> TYPE = new Type<>(DamageStats.id("storage_overview"));
    public static final StreamCodec<FriendlyByteBuf, StorageOverviewPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, StorageOverviewPacket::allowed,
            ByteBufCodecs.BOOL, StorageOverviewPacket::openScreen,
            StorageCleanupTarget.STREAM_CODEC, StorageOverviewPacket::completedCleanup,
            ByteBufCodecs.BOOL, StorageOverviewPacket::cleanupSucceeded,
            StorageOverview.STREAM_CODEC, StorageOverviewPacket::overview,
            StorageOverviewPacket::new
    );

    public static StorageOverviewPacket open(StorageOverview overview) {
        return new StorageOverviewPacket(true, true, StorageCleanupTarget.NONE, true, overview);
    }

    public static StorageOverviewPacket updated(StorageOverview overview) {
        return new StorageOverviewPacket(true, false, StorageCleanupTarget.NONE, true, overview);
    }

    public static StorageOverviewPacket updated(StorageOverview overview, StorageCleanupTarget completedCleanup,
                                                boolean cleanupSucceeded) {
        return new StorageOverviewPacket(true, false, completedCleanup, cleanupSucceeded, overview);
    }

    public static StorageOverviewPacket denied() {
        return new StorageOverviewPacket(false, false, StorageCleanupTarget.NONE, false, StorageOverview.EMPTY);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientStorageOverview.accept(this));
    }
}
