package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.registry.DSPackets;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.save.StatsStorage;
import io.zershyan.damagestats.stats.save.StorageMaintenance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;

/** 管理员按需刷新服务器存储概览，客户端不缓存或自行扫描服务器文件。 */
public record StorageOverviewRequestPacket() implements CustomPacketPayload {
    public static final StorageOverviewRequestPacket INSTANCE = new StorageOverviewRequestPacket();
    public static final Type<StorageOverviewRequestPacket> TYPE = new Type<>(DamageStats.id("storage_overview_request"));
    public static final StreamCodec<FriendlyByteBuf, StorageOverviewRequestPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

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
            DSPackets.sendToPlayer(player, StorageOverviewPacket.updated(
                    StorageMaintenance.overview(StatsStorage.directory(server), ServerStats.journal())));
        });
    }
}
