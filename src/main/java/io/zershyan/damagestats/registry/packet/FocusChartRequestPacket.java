package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.view.FocusChartPage;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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
        int cursor,
        int requestId
) implements CustomPacketPayload {
    public static final Type<FocusChartRequestPacket> TYPE = new Type<>(DamageStats.id("focus_chart"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusChartRequestPacket> STREAM_CODEC = StreamCodec.composite(
            StatsFilter.STREAM_CODEC, FocusChartRequestPacket::filter,
            FocusChartDimension.STREAM_CODEC, FocusChartRequestPacket::dimension,
            FocusChartScope.STREAM_CODEC, FocusChartRequestPacket::scope,
            DamageTypeGrouping.STREAM_CODEC, FocusChartRequestPacket::typeGrouping,
            ByteBufCodecs.VAR_INT, FocusChartRequestPacket::cursor,
            ByteBufCodecs.VAR_INT, FocusChartRequestPacket::requestId,
            FocusChartRequestPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(FocusChartRequestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            StatsFocusManager manager = ServerStats.focusManager();
            if(tracker == null || manager == null) return;
            if(!manager.canBrowse(player, payload.filter(), tracker)) {
                FocusChartPage denied = StatsViewBuilder.deniedFocusChartPage(manager.focusFor(player),
                        payload.dimension(), payload.scope(), payload.typeGrouping());
                PacketDistributor.sendToPlayer(player, new FocusChartPagePacket(denied.withRequestId(payload.requestId())));
                return;
            }
            FocusChartPage page = StatsViewBuilder.focusChartPage(tracker, player, manager.focusFor(player),
                    payload.filter(), payload.dimension(), payload.scope(), payload.typeGrouping(), payload.cursor());
            PacketDistributor.sendToPlayer(player, new FocusChartPagePacket(page.withRequestId(payload.requestId())));
        });
    }
}
