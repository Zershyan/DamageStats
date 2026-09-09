package io.zershyan.damagestats.registry;

import com.mojang.blaze3d.platform.InputConstants;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** 两个按键都归到统一的 DamageStats 分类下，玩家能在原版按键设置里改 */
@OnlyIn(Dist.CLIENT)
public final class DSKeyMappings {
    public static final KeyMapping OpenGui = new KeyMapping(
            DSKeyLang.OpenGuiKeyId, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, DSKeyLang.KeyCategoryId);

    public static final KeyMapping ToggleOverlay = new KeyMapping(
            DSKeyLang.ToggleOverlayKeyId, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, DSKeyLang.KeyCategoryId);

    public static void doRegister(IEventBus modEventBus) {
        modEventBus.addListener(DSKeyMappings::registerKeyMappings);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OpenGui);
        event.register(ToggleOverlay);
    }
}
