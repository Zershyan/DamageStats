package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.EntityGrouping;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.view.FocusChartPage;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 只在打开、切换条件、翻页或手动刷新时请求焦点图表。 */
public record FocusChartRequestPacket(
        StatsFilter filter,
        FocusChartDimension dimension,
        FocusChartScope scope,
        DamageTypeGrouping typeGrouping,
        EntityGrouping entityGrouping,
        String cursor,
        int requestId
) implements CustomPacketPayload {
    public static final Type<FocusChartRequestPacket> TYPE = new Type<>(DamageStats.id("focus_chart"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusChartRequestPacket> STREAM_CODEC =
            StreamCodec.of(FocusChartRequestPacket::encode, FocusChartRequestPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, FocusChartRequestPacket packet) {
        StatsFilter.STREAM_CODEC.encode(buf, packet.filter);
        FocusChartDimension.STREAM_CODEC.encode(buf, packet.dimension);
        FocusChartScope.STREAM_CODEC.encode(buf, packet.scope);
        DamageTypeGrouping.STREAM_CODEC.encode(buf, packet.typeGrouping);
        EntityGrouping.STREAM_CODEC.encode(buf, packet.entityGrouping);
        buf.writeUtf(packet.cursor, 128);
        buf.writeVarInt(packet.requestId);
    }

    private static FocusChartRequestPacket decode(RegistryFriendlyByteBuf buf) {
        return new FocusChartRequestPacket(
                StatsFilter.STREAM_CODEC.decode(buf),
                FocusChartDimension.STREAM_CODEC.decode(buf),
                FocusChartScope.STREAM_CODEC.decode(buf),
                DamageTypeGrouping.STREAM_CODEC.decode(buf),
                EntityGrouping.STREAM_CODEC.decode(buf),
                buf.readUtf(128),
                buf.readVarInt());
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
            if(!manager.canBrowse(player, filter(), tracker)) {
                FocusChartPage denied = StatsViewBuilder.deniedFocusChartPage(manager.focusFor(player),
                        dimension(), scope(), typeGrouping(), entityGrouping());
                PacketDistributor.sendToPlayer(player, new FocusChartPagePacket(denied.withRequestId(requestId())));
                return;
            }
            FocusChartPage page = StatsViewBuilder.focusChartPage(tracker, player, manager.focusFor(player),
                    filter(), dimension(), scope(), typeGrouping(),
                    entityGrouping(), cursor());
            PacketDistributor.sendToPlayer(player, new FocusChartPagePacket(page.withRequestId(requestId())));
        });
    }
}
