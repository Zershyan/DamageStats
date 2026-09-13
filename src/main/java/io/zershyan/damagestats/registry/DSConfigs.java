package io.zershyan.damagestats.registry;

import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.config.DSConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

public final class DSConfigs {
    public static void doRegister(Dist dist) {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, DSConfig.SPEC);
        if(dist.isClient()) {
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, DSClientConfig.SPEC);
        }
    }
}
