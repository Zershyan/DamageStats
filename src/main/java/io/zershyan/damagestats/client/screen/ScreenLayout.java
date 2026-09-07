package io.zershyan.damagestats.client.screen;

import java.util.ArrayList;
import java.util.List;

/** 小窗口下统一收缩边距并让按钮按行换行，避免各页面各自计算后发生重叠。 */
final class ScreenLayout {
    static int side(int screenWidth, int preferred) {
        return Math.min(preferred, Math.max(0, (screenWidth - 1) / 2));
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
            int buttonWidth = Math.min(Math.max(1, preferredWidth), available);
            if(x > left && x + buttonWidth > right) {
                x = left;
                y += height + gap;
            }
            bounds.add(new Bounds(x, y, buttonWidth, height));
            x += buttonWidth + gap;
        }
        return bounds;
    }

    static Flow bottomFlow(int screenWidth, int screenHeight, int preferredSide,
                           int preferredButtonHeight, int preferredGap, int padding,
                           int... preferredWidths) {
        List<Bounds> preferred = flow(screenWidth, preferredSide, 0, preferredButtonHeight,
                preferredGap, preferredWidths);
        int rows = preferred.stream().map(Bounds::y).distinct().mapToInt(ignored -> 1).sum();
        int safePadding = Math.min(Math.max(0, padding), Math.max(0, (screenHeight - 1) / 2));
        int available = Math.max(1, screenHeight - safePadding * 2);
        int gap = rows <= 1 ? 0 : Math.min(Math.max(0, preferredGap),
                Math.max(0, (available - rows) / (rows - 1)));
        int buttonHeight = Math.min(Math.max(1, preferredButtonHeight),
                Math.max(1, (available - gap * (rows - 1)) / Math.max(1, rows)));
        int totalHeight = safePadding * 2 + rows * buttonHeight + gap * (rows - 1);
        if(totalHeight > screenHeight) {
            safePadding = 0;
            totalHeight = Math.min(screenHeight, rows * buttonHeight + gap * (rows - 1));
        }
        int top = Math.max(0, screenHeight - totalHeight);
        return new Flow(top, buttonHeight, gap, totalHeight,
                flow(screenWidth, preferredSide, top + safePadding, buttonHeight, gap, preferredWidths));
    }

    static int bottom(List<Bounds> bounds, int fallback) {
        if(bounds.isEmpty()) return fallback;
        Bounds last = bounds.getLast();
        return last.y() + last.height();
    }

    static Bounds flowItem(List<Bounds> bounds, int index, Bounds fallback) {
        return index >= 0 && index < bounds.size() ? bounds.get(index) : fallback;
    }

    static Bounds centered(int screenWidth, int screenHeight, int preferredWidth, int preferredHeight) {
        int panelWidth = Math.min(Math.max(1, preferredWidth), Math.max(1, screenWidth));
        int panelHeight = Math.min(Math.max(1, preferredHeight), Math.max(1, screenHeight));
        return new Bounds(Math.max(0, (screenWidth - panelWidth) / 2),
                Math.max(0, (screenHeight - panelHeight) / 2), panelWidth, panelHeight);
    }

    static int clampTop(int preferredTop, int elementHeight, int screenHeight, int margin) {
        int maxTop = Math.max(0, screenHeight - Math.max(1, elementHeight) - Math.max(0, margin));
        return Math.clamp(preferredTop, Math.max(0, margin), maxTop);
    }

    static int availableHeight(int screenHeight, int top, int bottom, int gap) {
        return Math.max(0, screenHeight - Math.max(0, top) - Math.max(0, bottom) - Math.max(0, gap));
    }

    record Bounds(int x, int y, int width, int height) {}

    record Flow(int top, int buttonHeight, int gap, int height, List<Bounds> bounds) {}
}
