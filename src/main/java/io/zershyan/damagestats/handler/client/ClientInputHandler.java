package io.zershyan.damagestats.handler.client;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.DSKeyMappings;
import io.zershyan.damagestats.client.screen.StatsScreen;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.LockTargetPacket;
import io.zershyan.damagestats.registry.packet.StatsRequestPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 三个按键的响应。用 while(consumeClick()) 是原版惯例，一次 tick 里按了多下也不会漏 */
@EventBusSubscriber(modid = DamageStats.MODID, value = Dist.CLIENT)
public final class ClientInputHandler {
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if(player == null) return;
        while(DSKeyMappings.OpenGui.consumeClick()) openStats(minecraft);
        while(DSKeyMappings.ToggleOverlay.consumeClick()) toggleOverlay(player);
        while(DSKeyMappings.LockTarget.consumeClick()) lockTarget(minecraft, player);
    }

    /** 先发请求再开界面：界面每帧读客户端缓存，包一到就自动显示出来 */
    private static void openStats(Minecraft minecraft) {
        PacketDistributor.sendToServer(StatsRequestPacket.INSTANCE);
        minecraft.setScreen(new StatsScreen());
    }

    private static void toggleOverlay(LocalPlayer player) {
        boolean visible = !DSClientConfig.OverlayVisible.get();
        DSClientConfig.OverlayVisible.set(visible);
        DSClientConfig.OverlayVisible.save();
        player.displayClientMessage((visible ? DSKeyLang.OverlayShown : DSKeyLang.OverlayHidden).copy(), true);
    }

    /** 准星没指着东西就当作解除锁定，不用再单独占一个按键 */
    private static void lockTarget(Minecraft minecraft, LocalPlayer player) {
        Entity target = minecraft.crosshairPickEntity;
        if(target == null) {
            PacketDistributor.sendToServer(LockTargetPacket.CLEAR);
            player.displayClientMessage(DSKeyLang.TargetUnlocked.copy(), true);
            return;
        }
        PacketDistributor.sendToServer(new LockTargetPacket(target.getId()));
        player.displayClientMessage(DSKeyLang.TargetLocked.get(target.getDisplayName()), true);
    }
}
