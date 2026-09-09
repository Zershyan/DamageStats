package io.zershyan.damagestats;

import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(DamageStats.MODID)
public class DamageStats {
    public static final String MODID = "damagestats";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public DamageStats(IEventBus modEventBus, Dist dist, ModContainer modContainer) {
        IEventBus neoEventBus = NeoForge.EVENT_BUS;

        DamageTypeCategories.load();
        DSConfigs.doRegister(modContainer);
        DSPackets.doRegister(modEventBus);
        DSCommands.doRegister(neoEventBus);

        if(dist.isClient()) {
            DSKeyMappings.doRegister(modEventBus);
            DSOverlays.doRegister(modEventBus);
        }
    }
}
