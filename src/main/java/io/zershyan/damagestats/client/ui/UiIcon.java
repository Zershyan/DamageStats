package io.zershyan.damagestats.client.ui;

import net.minecraft.client.gui.GuiGraphics;

/** 图标画法。用方法引用指向 {@link UiDraw} 里的图标方法，控件只负责给出中心点、尺寸和颜色。 */
@FunctionalInterface
public interface UiIcon {
    /**
     * @param centerX   图标中心 X
     * @param centerY   图标中心 Y
     * @param size      图标外接方形边长
     * @param thickness 线宽
     * @param color     描边颜色
     */
    void draw(GuiGraphics graphics, float centerX, float centerY, float size, float thickness, int color);
}
