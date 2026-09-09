package io.zershyan.damagestats.handler.client;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.screen.StatsScreen;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.DSKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** 两个按键的响应。用 while(consumeClick()) 是原版惯例，一次 tick 里按了多下也不会漏 */
@EventBusSubscriber(modid = DamageStats.MODID, value = Dist.CLIENT)
public final class ClientInputHandler {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if(player == null) return;
        while(DSKeyMappings.OpenGui.consumeClick()) openStats(minecraft);
        while(DSKeyMappings.ToggleOverlay.consumeClick()) toggleOverlay(player);
    }

    /** 打开仪表盘后仅请求当前可见图表页，避免旧版全量快照占用网络。 */
    private static void openStats(Minecraft minecraft) {
        minecraft.setScreen(new StatsScreen());
    }

    private static void toggleOverlay(LocalPlayer player) {
        boolean visible = !DSClientConfig.OverlayVisible.get();
        DSClientConfig.OverlayVisible.set(visible);
        DSClientConfig.OverlayVisible.save();
        player.displayClientMessage((visible ? DSKeyLang.OverlayShown : DSKeyLang.OverlayHidden).copy(), true);
    }

}
