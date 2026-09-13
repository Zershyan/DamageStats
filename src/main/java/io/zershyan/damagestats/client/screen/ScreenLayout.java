package io.zershyan.damagestats.client.screen;

import net.minecraft.util.Mth;
import java.util.ArrayList;
import java.util.List;

/** 小窗口下统一收缩边距并计算控件位置，避免各页面各自计算后发生重叠。 */
final class ScreenLayout {
    static int side(int screenWidth, int preferred) {
        return Mth.clamp(preferred, 0, Math.max(0, (screenWidth - 1) / 2));
    }

    static int left(int screenWidth, int preferredSide) {
        return side(screenWidth, preferredSide);
    }

    static int right(int screenWidth, int preferredSide) {
        return Math.max(left(screenWidth, preferredSide) + 1, screenWidth - left(screenWidth, preferredSide));
    }

    static int width(int screenWidth, int preferredSide) {
        return Math.max(1, right(screenWidth, preferredSide) - left(screenWidth, preferredSide));
    }

    static List<Bounds> flow(int screenWidth, int preferredSide, int top, int height, int gap,
                             int... preferredWidths) {
        int left = left(screenWidth, preferredSide);
        int right = right(screenWidth, preferredSide);
        int available = Math.max(1, right - left);
        int x = left;
        int y = Math.max(0, top);
        List<Bounds> bounds = new ArrayList<>(preferredWidths.length);
        for(int preferredWidth : preferredWidths) {
            int buttonWidth = Mth.clamp(preferredWidth, 1, available);
            if(x > left && x + buttonWidth > right) {
                x = left;
                y += height + gap;
            }
            bounds.add(new Bounds(x, y, buttonWidth, height));
            x += buttonWidth + gap;
        }
        return bounds;
    }

    /** 将一组控件压缩到同一行，窗口变窄时也不自动换行。 */
    static List<Bounds> singleRow(int screenWidth, int preferredSide, int top, int height, int gap,
                                  int... preferredWidths) {
        int left = left(screenWidth, preferredSide);
        int right = right(screenWidth, preferredSide);
        int available = Math.max(1, right - left);
        if(preferredWidths.length == 0) return List.of();
        int itemCount = preferredWidths.length;
        int requestedGap = Math.max(0, gap);
        int effectiveGap = itemCount <= 1 ? 0
                : Mth.clamp((available - itemCount) / (itemCount - 1), 0, requestedGap);
        int gaps = (itemCount - 1) * effectiveGap;
        int content = Math.max(itemCount, available - gaps);
        int preferredTotal = 0;
        for(int width : preferredWidths) preferredTotal += Math.max(1, width);
        List<Bounds> bounds = new ArrayList<>(preferredWidths.length);
        int x = left;
        int used = 0;
        for(int index = 0; index < preferredWidths.length; index++) {
            int buttonWidth;
            if(index == preferredWidths.length - 1) {
                buttonWidth = Math.max(1, content - used);
            } else {
                buttonWidth = Math.max(1, Math.round((float) Math.max(1, preferredWidths[index])
                        * content / Math.max(1, preferredTotal)));
                buttonWidth = Mth.clamp(buttonWidth, 1, Math.max(1, content - used -
                        (preferredWidths.length - index - 1)));
            }
            bounds.add(new Bounds(x, Math.max(0, top), buttonWidth, Math.max(1, height)));
            x += buttonWidth + effectiveGap;
            used += buttonWidth;
        }
        return bounds;
    }

    static Flow bottomSingleRow(int screenWidth, int screenHeight, int preferredSide,
                                int preferredButtonHeight, int preferredGap, int padding,
                                int... preferredWidths) {
        int safePadding = Mth.clamp(padding, 0, Math.max(0, (screenHeight - 1) / 2));
        int buttonHeight = Mth.clamp(preferredButtonHeight, 1,
                Math.max(1, screenHeight - safePadding * 2));
        int totalHeight = safePadding * 2 + buttonHeight;
        int top = Math.max(0, screenHeight - totalHeight);
        return new Flow(top, buttonHeight, 0, totalHeight,
                singleRow(screenWidth, preferredSide, top + safePadding, buttonHeight, preferredGap,
                        preferredWidths));
    }

    static Flow bottomFlow(int screenWidth, int screenHeight, int preferredSide,
                           int preferredButtonHeight, int preferredGap, int padding,
                           int... preferredWidths) {
        return bottomSingleRow(screenWidth, screenHeight, preferredSide, preferredButtonHeight,
                preferredGap, padding, preferredWidths);
    }

    static int bottom(List<Bounds> bounds, int fallback) {
        if(bounds.isEmpty()) return fallback;
        Bounds last = bounds.get(bounds.size() - 1);
        return last.y() + last.height();
    }

    static Bounds flowItem(List<Bounds> bounds, int index, Bounds fallback) {
        return index >= 0 && index < bounds.size() ? bounds.get(index) : fallback;
    }

    static Bounds centered(int screenWidth, int screenHeight, int preferredWidth, int preferredHeight) {
        int panelWidth = Mth.clamp(preferredWidth, 1, Math.max(1, screenWidth));
        int panelHeight = Mth.clamp(preferredHeight, 1, Math.max(1, screenHeight));
        return new Bounds(Math.max(0, (screenWidth - panelWidth) / 2),
                Math.max(0, (screenHeight - panelHeight) / 2), panelWidth, panelHeight);
    }

    static int clampTop(int preferredTop, int elementHeight, int screenHeight, int margin) {
        int maxTop = Math.max(0, screenHeight - Math.max(1, elementHeight) - Math.max(0, margin));
        return Mth.clamp(preferredTop, Math.max(0, margin), maxTop);
    }

    static int availableHeight(int screenHeight, int top, int bottom, int gap) {
        return Math.max(0, screenHeight - Math.max(0, top) - Math.max(0, bottom) - Math.max(0, gap));
    }

    record Bounds(int x, int y, int width, int height) {}

    record Flow(int top, int buttonHeight, int gap, int height, List<Bounds> bounds) {}
}
