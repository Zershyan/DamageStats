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
     * 横向切分。weights 为每列宽度：{@link #FILL} 表示「吃掉固定列之外的剩余空间」，
     * 出现 FILL 时其余数值按像素宽处理；一个 FILL 都没有时，数值按权重比例分配。
     * 最后一列总是拿走剩下的像素，避免累计取整后留缝。
     */
    public static List<Rect> row(Rect area, int gap, int... weights) {
        List<Rect> result = new ArrayList<>(weights.length);
        if(weights.length == 0) return result;
        int totalGap = gap * (weights.length - 1);
        int available = Math.max(weights.length, area.width() - totalGap);
        int fillCount = 0;
        int fixedTotal = 0;
        int weightTotal = 0;
        for(int weight : weights) {
            if(weight == FILL) {
                fillCount++;
            } else {
                fixedTotal += Math.max(1, weight);
                weightTotal += Math.max(1, weight);
            }
        }
        // FILL 列平分「可用宽度 - 固定列」；没有 FILL 时退回按权重比例分配
        int fillWidth = fillCount == 0 ? 0
                : Math.max(1, Math.max(fillCount, available - fixedTotal) / fillCount);
        int used = 0;
        for(int index = 0; index < weights.length; index++) {
            int width;
            if(index == weights.length - 1) {
                width = Math.max(1, available - used);
            } else if(weights[index] == FILL) {
                width = fillWidth;
            } else if(fillCount > 0) {
                width = Math.max(1, weights[index]);
            } else {
                width = Math.max(1, Math.round((float) Math.max(1, weights[index]) * available / weightTotal));
            }
            width = Math.min(width, Math.max(1, available - used - (weights.length - index - 1)));
            result.add(new Rect(area.x() + used + gap * index, area.y(), width, area.height()));
            used += width;
        }
        return result;
    }

    /** 等分的横向切分。 */
    public static List<Rect> row(Rect area, int gap, int count) {
        if(count <= 0) return List.of();
        int[] weights = new int[count];
        for(int index = 0; index < count; index++) weights[index] = 1;
        return row(area, gap, weights);
    }

    /** 横向排列固定宽度的若干控件并整体右对齐，宽度不足时按比例压缩。 */
    public static List<Rect> rowRight(Rect area, int gap, int... widths) {
        int totalGap = gap * Math.max(0, widths.length - 1);
        int available = Math.max(widths.length, area.width() - totalGap);
        List<Rect> result = new ArrayList<>(widths.length);
        int totalWidth = 0;
        for(int width : widths) totalWidth += Math.max(1, width);
        int used = 0;
        for(int index = 0; index < widths.length; index++) {
            int width;
            if(index == widths.length - 1) {
                width = Math.max(1, available - used);
            } else {
                width = Math.max(1, Math.round((float) Math.max(1, widths[index]) * available / totalWidth));
                width = Math.min(width, Math.max(1, available - used - (widths.length - index - 1)));
            }
            result.add(new Rect(area.right() - available + used + gap * index, area.y(), width, area.height()));
            used += width;
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
