package io.zershyan.damagestats.registry;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.CustomPacketPayload.IPayloadContext;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.registry.packet.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.function.BiConsumer;

public final class DSPackets {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(DamageStats.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);
    private static int nextMessageId;

    public static void doRegister() {
        registerClient(StatsSummaryPacket.class, StatsSummaryPacket.STREAM_CODEC, StatsSummaryPacket::handle);
        registerClient(FocusStatePacket.class, FocusStatePacket.STREAM_CODEC, FocusStatePacket::handle);
        registerClient(EntityChoicePagePacket.class, EntityChoicePagePacket.STREAM_CODEC, EntityChoicePagePacket::handle);
        registerClient(FocusChartPagePacket.class, FocusChartPagePacket.STREAM_CODEC, FocusChartPagePacket::handle);
        registerClient(FocusChartInstancesPagePacket.class, FocusChartInstancesPagePacket.STREAM_CODEC,
                FocusChartInstancesPagePacket::handle);
        registerClient(HistoryPagePacket.class, HistoryPagePacket.STREAM_CODEC, HistoryPagePacket::handle);
        registerClient(ExportStartPacket.class, ExportStartPacket.STREAM_CODEC, ExportStartPacket::handle);
        registerClient(ExportChunkPacket.class, ExportChunkPacket.STREAM_CODEC, ExportChunkPacket::handle);
        registerClient(StorageOverviewPacket.class, StorageOverviewPacket.STREAM_CODEC, StorageOverviewPacket::handle);
        registerClient(StatsInvalidatedPacket.class, StatsInvalidatedPacket.STREAM_CODEC, StatsInvalidatedPacket::handle);

        registerServer(FocusSetPacket.class, FocusSetPacket.STREAM_CODEC, FocusSetPacket::handle);
        registerServer(FocusStateRequestPacket.class, FocusStateRequestPacket.STREAM_CODEC,
                FocusStateRequestPacket::handle);
        registerServer(EntityChoiceRequestPacket.class, EntityChoiceRequestPacket.STREAM_CODEC,
                EntityChoiceRequestPacket::handle);
        registerServer(FocusChartRequestPacket.class, FocusChartRequestPacket.STREAM_CODEC,
                FocusChartRequestPacket::handle);
        registerServer(FocusChartInstancesRequestPacket.class, FocusChartInstancesRequestPacket.STREAM_CODEC,
                FocusChartInstancesRequestPacket::handle);
        registerServer(HistoryRequestPacket.class, HistoryRequestPacket.STREAM_CODEC, HistoryRequestPacket::handle);
        registerServer(ExportRequestPacket.class, ExportRequestPacket.STREAM_CODEC, ExportRequestPacket::handle);
        registerServer(StorageOverviewRequestPacket.class, StorageOverviewRequestPacket.STREAM_CODEC,
                StorageOverviewRequestPacket::handle);
        registerServer(StorageCleanupPacket.class, StorageCleanupPacket.STREAM_CODEC, StorageCleanupPacket::handle);
        registerServer(FocusGuiClosedPacket.class, FocusGuiClosedPacket.STREAM_CODEC, FocusGuiClosedPacket::handle);
        registerServer(ResetStatsPacket.class, ResetStatsPacket.STREAM_CODEC, ResetStatsPacket::handle);
    }

    public static <T extends CustomPacketPayload> void sendToServer(T message) {
        CHANNEL.sendToServer(message);
    }

    public static <T extends CustomPacketPayload> void sendToPlayer(ServerPlayer player, T message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static <T extends CustomPacketPayload> void sendToAllPlayers(T message) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), message);
    }

    private static <T extends CustomPacketPayload> void registerClient(
            Class<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<T, IPayloadContext> handler) {
        register(type, codec, handler, NetworkDirection.PLAY_TO_CLIENT);
    }

    private static <T extends CustomPacketPayload> void registerServer(
            Class<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<T, IPayloadContext> handler) {
        register(type, codec, handler, NetworkDirection.PLAY_TO_SERVER);
    }

    private static <T extends CustomPacketPayload> void register(
            Class<T> type, StreamCodec<FriendlyByteBuf, T> codec, BiConsumer<T, IPayloadContext> handler,
            NetworkDirection direction) {
        CHANNEL.registerMessage(nextMessageId++, type, (message, buffer) -> codec.encode(buffer, message), buffer -> codec.decode(buffer),
                (message, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    handler.accept(message, new ForgePayloadContext(context));
                    context.setPacketHandled(true);
                }, Optional.of(direction));
    }

    private static final class ForgePayloadContext implements IPayloadContext {
        private final NetworkEvent.Context context;

        private ForgePayloadContext(NetworkEvent.Context context) {
            this.context = context;
        }

        @Override
        public ServerPlayer player() {
            return context.getSender();
        }

        @Override
        public void enqueueWork(Runnable work) {
            context.enqueueWork(work);
        }
    }
}
