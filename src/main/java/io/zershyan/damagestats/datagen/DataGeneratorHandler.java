package io.zershyan.damagestats.datagen;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.datagen.provider.DSLangProvider;
import io.zershyan.damagestats.datagen.provider.PackMetadataProvider;
import net.minecraft.data.PackOutput;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = DamageStats.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DataGeneratorHandler {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        PackOutput packOutput = event.getGenerator().getPackOutput();

        event.getGenerator().addProvider(event.includeClient(), DSLangProvider.runEnUs(packOutput));
        event.getGenerator().addProvider(event.includeClient(), DSLangProvider.runZhCn(packOutput));
        event.getGenerator().addProvider(event.includeClient(), new PackMetadataProvider(packOutput));
    }
}
