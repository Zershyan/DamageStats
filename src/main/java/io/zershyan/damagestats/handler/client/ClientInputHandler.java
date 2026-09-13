package io.zershyan.damagestats.handler.client;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.screen.StatsScreen;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.DSKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/** 两个按键的响应。用 while(consumeClick()) 是原版惯例，一次 tick 里按了多下也不会漏 */
@EventBusSubscriber(modid = DamageStats.MODID, value = Dist.CLIENT)
public final class ClientInputHandler {
    private static @Nullable StatsScreen statsScreen;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if(event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if(player == null) return;
        while(DSKeyMappings.OpenGui.consumeClick()) openStats(minecraft);
        while(DSKeyMappings.ToggleOverlay.consumeClick()) toggleOverlay(player);
    }

    /** 打开仪表盘后仅请求当前可见图表页，避免旧版全量快照占用网络。 */
    private static void openStats(Minecraft minecraft) {
        if(statsScreen == null) statsScreen = new StatsScreen();
        statsScreen.refreshOnOpen();
        minecraft.setScreen(statsScreen);
    }

    public static void clearCachedScreen() {
        statsScreen = null;
    }

    private static void toggleOverlay(LocalPlayer player) {
        boolean visible = !DSClientConfig.OverlayVisible.get();
        DSClientConfig.OverlayVisible.set(visible);
        DSClientConfig.OverlayVisible.save();
        player.displayClientMessage((visible ? DSKeyLang.OverlayShown : DSKeyLang.OverlayHidden).copy(), true);
    }

}
