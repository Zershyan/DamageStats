package io.zershyan.damagestats.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 文字可能被截断的控件：悬停或键盘聚焦时交出完整文本，由界面画成提示浮层。 */
public interface UiTruncatable {
    /** 当前处于焦点且文字被截断时返回完整文本，否则返回 null。 */
    @Nullable Component focusedFullText();

    /** 键盘导航留下的焦点才算；鼠标点击也会让控件获得焦点，不能据此弹提示。 */
    static boolean keyboardFocused(AbstractWidget widget) {
        return widget.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
    }
}
