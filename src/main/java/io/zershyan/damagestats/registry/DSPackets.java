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
        registrar.playToClient(StatsSnapshotPacket.TYPE, StatsSnapshotPacket.STREAM_CODEC, StatsSnapshotPacket::handle);
        registrar.playToClient(StatsInvalidatedPacket.TYPE, StatsInvalidatedPacket.STREAM_CODEC, StatsInvalidatedPacket::handle);
        //server
        registrar.playToServer(StatsRequestPacket.TYPE, StatsRequestPacket.STREAM_CODEC, StatsRequestPacket::handle);
        registrar.playToServer(OverlayTargetTypePacket.TYPE, OverlayTargetTypePacket.STREAM_CODEC,
                OverlayTargetTypePacket::handle);
        registrar.playToServer(ResetStatsPacket.TYPE, ResetStatsPacket.STREAM_CODEC, ResetStatsPacket::handle);
    }
}
