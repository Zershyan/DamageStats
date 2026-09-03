package io.zershyan.damagestats;

import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.config.DamageTypeCategories;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(DamageStats.MODID)
public class DamageStats {
    public static final String MODID = "damagestats";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public DamageStats(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, DSConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, DSClientConfig.SPEC);
        DamageTypeCategories.load();
    }
}
