package io.zershyan.damagestats;

import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(DamageStats.MODID)
public class DamageStats {
    public static final String MODID = "damagestats";

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }

    public DamageStats() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        Dist dist = FMLEnvironment.dist;

        DamageTypeCategories.load();
        DSConfigs.doRegister(dist);
        DSPackets.doRegister();
        DSCommands.doRegister(MinecraftForge.EVENT_BUS);

        if(dist.isClient()) {
            DSKeyMappings.doRegister(modEventBus);
            DSOverlays.doRegister(modEventBus);
        }
    }
}
