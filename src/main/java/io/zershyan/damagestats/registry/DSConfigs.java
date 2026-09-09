package io.zershyan.damagestats.registry;

import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.config.DSConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class DSConfigs {
    public static void registerCommon(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, DSConfig.SPEC);
    }

    public static void registerClient(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, DSClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    public static void doRegister(ModContainer container) {
        if(FMLLoader.getDist().isClient()) registerClient(container);
        registerCommon(container);
    }
}
