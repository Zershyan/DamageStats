package io.zershyan.damagestats.client.ui;

import java.util.ArrayList;
import java.util.List;

/** 布局代数：用不可变的 {@link Rect} 反复切分区域，代替各页面各自算坐标。 */
public final class UiLayout {
    /** 权重含义为「吃掉剩余空间」的标记值。 */
    public static final int FILL = -1;

    /** 一块矩形区域。用 take* 系列切分时不会修改自身，方便从一个母区域派生出多个子区域。 */
    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public int centerX() {
            return x + width / 2;
        }

        public int centerY() {
            return y + height / 2;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        public Rect inset(int amount) {
            return inset(amount, amount);
        }

        public Rect inset(int horizontal, int vertical) {
            return new Rect(x + horizontal, y + vertical,
                    Math.max(0, width - horizontal * 2), Math.max(0, height - vertical * 2));
        }

        /** 整体平移，用于把一整组已排好的位置按滚动偏移搬移。 */
        public Rect translated(int deltaX, int deltaY) {
            return new Rect(x + deltaX, y + deltaY, width, height);
        }

        /** 取顶部一条，高度会被裁到不超过自身。 */
        public Rect takeTop(int amount) {
            int taken = Math.clamp(amount, 0, height);
            return new Rect(x, y, width, taken);
        }

        public Rect takeBottom(int amount) {
            int taken = Math.clamp(amount, 0, height);
            return new Rect(x, bottom() - taken, width, taken);
        }

        public Rect takeLeft(int amount) {
            int taken = Math.clamp(amount, 0, width);
            return new Rect(x, y, taken, height);
        }

        public Rect takeRight(int amount) {
            int taken = Math.clamp(amount, 0, width);
            return new Rect(right() - taken, y, taken, height);
        }

        /** 去掉顶部一条后的剩余区域。 */
        public Rect dropTop(int amount, int gap) {
            int consumed = Math.clamp(amount + gap, 0, height);
            return new Rect(x, y + consumed, width, height - consumed);
        }

        public Rect dropBottom(int amount, int gap) {
            int consumed = Math.clamp(amount + gap, 0, height);
            return new Rect(x, y, width, height - consumed);
        }

        public Rect dropLeft(int amount, int gap) {
            int consumed = Math.clamp(amount + gap, 0, width);
            return new Rect(x + consumed, y, width - consumed, height);
        }

        public Rect dropRight(int amount, int gap) {
            int consumed = Math.clamp(amount + gap, 0, width);
            return new Rect(x, y, width - consumed, height);
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }

    }

    /**
     * 横向切分。widths 是每列的**像素宽度**：{@link #FILL} 表示「吃掉固定列之外的剩余空间」，
     * 其余按给定宽度从左侧依次摆放；合计放不下时整体按比例压缩。
     * <p>
     * 合计小于区域宽度时**右边留白**，不会为了填满而把列拉宽——否则传 72px 的按钮会被撑成整行，
     * 和相邻控件叠在一起。
     */
    public static List<Rect> row(Rect area, int gap, int... widths) {
        return pack(area, gap, false, widths);
    }

    /** 等分的横向切分：每列吃掉等份的剩余空间，合计正好铺满区域。 */
    public static List<Rect> row(Rect area, int gap, int count) {
        if(count <= 0) return List.of();
        int[] widths = new int[count];
        for(int index = 0; index < count; index++) widths[index] = FILL;
        return row(area, gap, widths);
    }

    /** 横向排列固定宽度的若干控件并整体右对齐，宽度不足时按比例压缩。 */
    public static List<Rect> rowRight(Rect area, int gap, int... widths) {
        return pack(area, gap, true, widths);
    }

    /**
     * 两套切分的共同实现。列宽一律按像素解析，FILL 列均分固定列之外的剩余（取整余数补给最后一个
     * FILL 列，保证合计刚好铺满），rightAligned 决定整块靠哪一侧摆。
     */
    private static List<Rect> pack(Rect area, int gap, boolean rightAligned, int... widths) {
        List<Rect> result = new ArrayList<>(widths.length);
        if(widths.length == 0) return result;
        int totalGap = gap * (widths.length - 1);
        int available = Math.max(widths.length, area.width() - totalGap);
        int fillCount = 0;
        int total = 0;
        int[] resolved = new int[widths.length];
        for(int index = 0; index < widths.length; index++) {
            if(widths[index] == FILL) {
                fillCount++;
            } else {
                resolved[index] = Math.max(1, widths[index]);
                total += resolved[index];
            }
        }
        if(fillCount > 0) {
            int budget = Math.max(fillCount, available - total);
            int each = budget / fillCount;
            int extra = budget - each * fillCount;
            for(int index = widths.length - 1; index >= 0; index--) {
                if(widths[index] != FILL) continue;
                resolved[index] = each + extra;
                extra = 0;
            }
            total += budget;
        }
        if(total > available) {
            // 逐列取整会把误差累积到最后一列，所以先算前面几列，最后一列直接拿走剩下的像素
            int used = 0;
            for(int index = 0; index < widths.length - 1; index++) {
                resolved[index] = Math.max(1, resolved[index] * available / total);
                used += resolved[index];
            }
            resolved[widths.length - 1] = Math.max(1, available - used);
        }
        int originX = area.x() + (rightAligned ? Math.max(0, available - total) : 0);
        int used = 0;
        for(int index = 0; index < widths.length; index++) {
            result.add(new Rect(originX + used + gap * index, area.y(), resolved[index], area.height()));
            used += resolved[index];
        }
        return result;
    }

    /** 自动换行的横向流式布局，窄窗口下把控件推到下一行。 */
    public static List<Rect> wrap(Rect area, int gap, int itemHeight, int... widths) {
        List<Rect> result = new ArrayList<>(widths.length);
        int x = area.x();
        int y = area.y();
        for(int preferred : widths) {
            int width = Math.clamp(preferred, 1, Math.max(1, area.width()));
            if(x > area.x() && x + width > area.right()) {
                x = area.x();
                y += itemHeight + gap;
            }
            result.add(new Rect(x, y, width, itemHeight));
            x += width + gap;
        }
        return result;
    }

    /** 一列控件里第 index 个的位置，越界时回退到 fallback。 */
    public static Rect item(List<Rect> rects, int index, Rect fallback) {
        return index >= 0 && index < rects.size() ? rects.get(index) : fallback;
    }
}
