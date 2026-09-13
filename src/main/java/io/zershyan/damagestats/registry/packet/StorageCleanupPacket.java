package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.handler.common.StatsResetService;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.registry.DSPackets;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.save.StatsStorage;
import io.zershyan.damagestats.stats.save.StorageCleanupTarget;
import io.zershyan.damagestats.stats.save.StorageMaintenance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/** 所有磁盘清理都在服务端重新核验管理员权限，客户端按钮不具备授权能力。 */
public record StorageCleanupPacket(StorageCleanupTarget target) implements CustomPacketPayload {
    public static final Type<StorageCleanupPacket> TYPE = new Type<>(DamageStats.id("storage_cleanup"));
    public static final StreamCodec<FriendlyByteBuf, StorageCleanupPacket> STREAM_CODEC = StreamCodec.composite(
            StorageCleanupTarget.STREAM_CODEC, StorageCleanupPacket::target,
            StorageCleanupPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            if(!StatsFocusManager.hasFullAccess(player)) {
                DSPackets.sendToPlayer(player, StorageOverviewPacket.denied());
                return;
            }
            MinecraftServer server = player.getServer();
            if(server == null) return;
            boolean succeeded = switch (target()) {
                case TEMPORARY_AND_BACKUPS -> StorageMaintenance.cleanTemporaryAndBackups(
                        StatsStorage.directory(server), ServerStats.journal());
                case EXPORTS -> StorageMaintenance.cleanExports(StatsStorage.directory(server));
                case ALL_RECORDS -> StatsResetService.resetAll(server);
                case NONE -> true;
            };
            DSPackets.sendToPlayer(player, StorageOverviewPacket.updated(
                    StorageMaintenance.overview(StatsStorage.directory(server), ServerStats.journal()),
                    target(), succeeded));
        });
    }
}
