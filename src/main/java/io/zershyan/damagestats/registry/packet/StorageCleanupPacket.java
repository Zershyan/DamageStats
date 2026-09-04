package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.handler.common.StatsResetService;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.save.StatsStorage;
import io.zershyan.damagestats.stats.save.StorageCleanupTarget;
import io.zershyan.damagestats.stats.save.StorageMaintenance;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 所有磁盘清理都在服务端重新核验管理员权限，客户端按钮不具备授权能力。 */
public record StorageCleanupPacket(StorageCleanupTarget target) implements CustomPacketPayload {
    public static final Type<StorageCleanupPacket> TYPE = new Type<>(DamageStats.id("storage_cleanup"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StorageCleanupPacket> STREAM_CODEC = StreamCodec.composite(
            StorageCleanupTarget.STREAM_CODEC, StorageCleanupPacket::target,
            StorageCleanupPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StorageCleanupPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            if(!player.hasPermissions(2)) {
                PacketDistributor.sendToPlayer(player, StorageOverviewPacket.denied());
                return;
            }
            switch (payload.target()) {
                case TEMPORARY_AND_BACKUPS -> StorageMaintenance.cleanTemporaryAndBackups(
                        StatsStorage.directory(player.getServer()));
                case EXPORTS -> StorageMaintenance.cleanExports(StatsStorage.directory(player.getServer()));
                case ALL_RECORDS -> StatsResetService.resetAll(player.getServer());
                case NONE -> {
                    PacketDistributor.sendToPlayer(player, StorageOverviewPacket.updated(
                            StorageMaintenance.overview(StatsStorage.directory(player.getServer()), ServerStats.journal()),
                            StorageCleanupTarget.NONE));
                    return;
                }
            }
            PacketDistributor.sendToPlayer(player, StorageOverviewPacket.updated(
                    StorageMaintenance.overview(StatsStorage.directory(player.getServer()), ServerStats.journal()),
                    payload.target()));
        });
    }
}
