package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.filter.FilterKey;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.view.FocusChartInstancesPage;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 请求焦点图表某个实体类型下的实例子行。 */
public record FocusChartInstancesRequestPacket(
        StatsFilter filter,
        FocusChartDimension dimension,
        FocusChartScope scope,
        DamageTypeGrouping typeGrouping,
        FilterKey parentKey,
        String cursor,
        int requestId
) implements CustomPacketPayload {
    public static final Type<FocusChartInstancesRequestPacket> TYPE =
            new Type<>(DamageStats.id("focus_chart_instances"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusChartInstancesRequestPacket> STREAM_CODEC =
            StreamCodec.of(FocusChartInstancesRequestPacket::encode, FocusChartInstancesRequestPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, FocusChartInstancesRequestPacket packet) {
        StatsFilter.STREAM_CODEC.encode(buf, packet.filter);
        FocusChartDimension.STREAM_CODEC.encode(buf, packet.dimension);
        FocusChartScope.STREAM_CODEC.encode(buf, packet.scope);
        DamageTypeGrouping.STREAM_CODEC.encode(buf, packet.typeGrouping);
        FilterKey.STREAM_CODEC.encode(buf, packet.parentKey);
        buf.writeUtf(packet.cursor, 128);
        buf.writeVarInt(packet.requestId);
    }

    private static FocusChartInstancesRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new FocusChartInstancesRequestPacket(
                StatsFilter.STREAM_CODEC.decode(buf), FocusChartDimension.STREAM_CODEC.decode(buf),
                FocusChartScope.STREAM_CODEC.decode(buf), DamageTypeGrouping.STREAM_CODEC.decode(buf),
                FilterKey.STREAM_CODEC.decode(buf), buf.readUtf(128), buf.readVarInt());
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            StatsFocusManager manager = ServerStats.focusManager();
            if(tracker == null || manager == null) return;
            StatsFilter instanceFilter = StatsViewBuilder.instanceFilter(filter(), dimension(), parentKey());
            if(!manager.canBrowse(player, filter(), tracker)
                    || instanceFilter == null || !manager.canBrowse(player, instanceFilter, tracker)) {
                sendDenied(manager, player);
                return;
            }
            FocusChartInstancesPage page = StatsViewBuilder.focusChartInstancesPage(tracker, player,
                    manager.focusFor(player), filter(), dimension(), scope(), typeGrouping(), parentKey(), cursor());
            PacketDistributor.sendToPlayer(player, new FocusChartInstancesPagePacket(page.withRequestId(requestId())));
        });
    }

    private void sendDenied(StatsFocusManager manager, ServerPlayer player) {
        FocusChartInstancesPage denied = StatsViewBuilder.deniedFocusChartInstancesPage(manager.focusFor(player),
                dimension(), scope(), typeGrouping(), filter(), parentKey());
        PacketDistributor.sendToPlayer(player, new FocusChartInstancesPagePacket(denied.withRequestId(requestId())));
    }
}
