package io.zershyan.damagestats.handler.client;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.DSKeyMappings;
import io.zershyan.damagestats.client.screen.StatsScreen;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.StatsRequestPacket;
import io.zershyan.damagestats.stats.EntityRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 两个按键的响应。用 while(consumeClick()) 是原版惯例，一次 tick 里按了多下也不会漏 */
@EventBusSubscriber(modid = DamageStats.MODID, value = Dist.CLIENT)
public final class ClientInputHandler {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if(player == null) return;
        while(DSKeyMappings.OpenGui.consumeClick()) openStats(minecraft, player);
        while(DSKeyMappings.ToggleOverlay.consumeClick()) toggleOverlay(player);
    }

    /** 先发请求再开界面：界面每帧读客户端缓存，包一到就自动显示出来 */
    private static void openStats(Minecraft minecraft, LocalPlayer player) {
        PacketDistributor.sendToServer(StatsRequestPacket.forSelf(EntityRef.of(player)));
        minecraft.setScreen(new StatsScreen());
    }

    private static void toggleOverlay(LocalPlayer player) {
        boolean visible = !DSClientConfig.OverlayVisible.get();
        DSClientConfig.OverlayVisible.set(visible);
        DSClientConfig.OverlayVisible.save();
        player.displayClientMessage((visible ? DSKeyLang.OverlayShown : DSKeyLang.OverlayHidden).copy(), true);
    }

}
