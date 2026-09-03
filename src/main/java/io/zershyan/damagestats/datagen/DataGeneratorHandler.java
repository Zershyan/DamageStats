package io.zershyan.damagestats.datagen;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.datagen.provider.DSLangProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.data.event.GatherDataEvent;

@EventBusSubscriber(modid = DamageStats.MODID)
public final class DataGeneratorHandler {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        event.createProvider(DSLangProvider::runEnUs);
        event.createProvider(DSLangProvider::runZhCn);
    }

    private DataGeneratorHandler() {
    }
}
