package io.zershyan.damagestats.registry;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.overlay.StatsOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.IEventBus;

@OnlyIn(Dist.CLIENT)
public final class DSOverlays {
    public static void doRegister(IEventBus modEventBus) {
        modEventBus.addListener(DSOverlays::registerGuiOverlays);
    }

    private static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(DamageStats.MODID + "_stats_overlay", new StatsOverlay());
    }
}
