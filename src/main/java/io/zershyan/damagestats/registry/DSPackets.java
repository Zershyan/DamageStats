package io.zershyan.damagestats.registry;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.registry.packet.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforgespi.language.IModInfo;
import org.jetbrains.annotations.NotNull;

@EventBusSubscriber(modid = DamageStats.MODID)
public class DSPackets {
    /** 协议版本跟着 mod 版本走，改版本自动让旧协议失效 */
    @NotNull
    private static final String PROTOCOL_VERSION = ModList.get()
            .getModContainerById(DamageStats.MODID)
            .map(ModContainer::getModInfo)
            .map(IModInfo::getVersion)
            .map(Object::toString)
            .orElse("unknown");

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        //client
        registrar.playToClient(StatsSummaryPacket.TYPE, StatsSummaryPacket.STREAM_CODEC, StatsSummaryPacket::handle);
        registrar.playToClient(FocusStatePacket.TYPE, FocusStatePacket.STREAM_CODEC, FocusStatePacket::handle);
        registrar.playToClient(EntityChoicePagePacket.TYPE, EntityChoicePagePacket.STREAM_CODEC,
                EntityChoicePagePacket::handle);
        registrar.playToClient(FocusChartPagePacket.TYPE, FocusChartPagePacket.STREAM_CODEC,
                FocusChartPagePacket::handle);
        registrar.playToClient(HistoryPagePacket.TYPE, HistoryPagePacket.STREAM_CODEC, HistoryPagePacket::handle);
        registrar.playToClient(ExportStartPacket.TYPE, ExportStartPacket.STREAM_CODEC, ExportStartPacket::handle);
        registrar.playToClient(ExportChunkPacket.TYPE, ExportChunkPacket.STREAM_CODEC, ExportChunkPacket::handle);
        registrar.playToClient(StorageOverviewPacket.TYPE, StorageOverviewPacket.STREAM_CODEC, StorageOverviewPacket::handle);
        registrar.playToClient(StatsInvalidatedPacket.TYPE, StatsInvalidatedPacket.STREAM_CODEC, StatsInvalidatedPacket::handle);
        //server
        registrar.playToServer(FocusSetPacket.TYPE, FocusSetPacket.STREAM_CODEC, FocusSetPacket::handle);
        registrar.playToServer(FocusStateRequestPacket.TYPE, FocusStateRequestPacket.STREAM_CODEC,
                FocusStateRequestPacket::handle);
        registrar.playToServer(EntityChoiceRequestPacket.TYPE, EntityChoiceRequestPacket.STREAM_CODEC,
                EntityChoiceRequestPacket::handle);
        registrar.playToServer(FocusChartRequestPacket.TYPE, FocusChartRequestPacket.STREAM_CODEC,
                FocusChartRequestPacket::handle);
        registrar.playToServer(HistoryRequestPacket.TYPE, HistoryRequestPacket.STREAM_CODEC, HistoryRequestPacket::handle);
        registrar.playToServer(ExportRequestPacket.TYPE, ExportRequestPacket.STREAM_CODEC, ExportRequestPacket::handle);
        registrar.playToServer(StorageOverviewRequestPacket.TYPE, StorageOverviewRequestPacket.STREAM_CODEC,
                StorageOverviewRequestPacket::handle);
        registrar.playToServer(StorageCleanupPacket.TYPE, StorageCleanupPacket.STREAM_CODEC, StorageCleanupPacket::handle);
        registrar.playToServer(FocusGuiClosedPacket.TYPE, FocusGuiClosedPacket.STREAM_CODEC, FocusGuiClosedPacket::handle);
        registrar.playToServer(ResetStatsPacket.TYPE, ResetStatsPacket.STREAM_CODEC, ResetStatsPacket::handle);
    }
}
