package io.zershyan.damagestats.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** 三个按键都归到统一的 DamageStats 分类下，玩家能在原版按键设置里改 */
@EventBusSubscriber(modid = DamageStats.MODID, value = Dist.CLIENT)
public final class DSKeyMappings {
    public static final KeyMapping OpenGui = new KeyMapping(
            DSKeyLang.OpenGuiKeyId, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, DSKeyLang.KeyCategoryId);

    public static final KeyMapping ToggleOverlay = new KeyMapping(
            DSKeyLang.ToggleOverlayKeyId, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, DSKeyLang.KeyCategoryId);

    public static final KeyMapping LockTarget = new KeyMapping(
            DSKeyLang.LockTargetKeyId, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, DSKeyLang.KeyCategoryId);

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OpenGui);
        event.register(ToggleOverlay);
        event.register(LockTarget);
    }

    private DSKeyMappings() {
    }
}
