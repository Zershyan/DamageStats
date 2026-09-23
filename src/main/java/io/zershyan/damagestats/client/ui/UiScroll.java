package io.zershyan.damagestats.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 平滑滚动。滚轮改的是目标偏移，绘制用的是带缓动的当前偏移——
 * 离散跳变是「像 MC」和「像网页」之间最明显的一条分界线，值得单独抽一个组件。
 * <p>
 * 滚动条只在内容真的溢出时出现，并跟随当前偏移；停止滚动后会淡出，不长期占位置。
 */
public final class UiScroll {
    /** 指数逼近的时间常数：越小越快。约 0.2 秒到位。 */
    private static final float FOLLOW_SECONDS = 0.07F;
    private static final float INDICATOR_HOLD_SECONDS = 0.7F;
    private static final float INDICATOR_FADE_SECONDS = 0.35F;

    private float position;
    private int target;
    private int contentHeight;
    private int viewportHeight;
    private float indicatorAmount;
    private float idleSeconds = INDICATOR_HOLD_SECONDS;

    /** 每次布局或内容变化后调用，偏移会被夹回合法范围。 */
    public void measure(int contentHeight, int viewportHeight) {
        this.contentHeight = Math.max(0, contentHeight);
        this.viewportHeight = Math.max(0, viewportHeight);
        target = Mth.clamp(target, 0, maxOffset());
        position = Mth.clamp(position, 0F, maxOffset());
    }

    public int offset() {
        return Math.round(position);
    }

    public int target() {
        return target;
    }

    public boolean scrollable() {
        return maxOffset() > 0;
    }

    public void reset() {
        position = 0F;
        target = 0;
        idleSeconds = INDICATOR_HOLD_SECONDS;
    }

    /** 滚到指定偏移。 */
    public void scrollTo(int offset) {
        target = Mth.clamp(offset, 0, maxOffset());
        idleSeconds = 0F;
    }

    /**
     * @param wheelSteps 滚轮格数，正数向下
     * @param step       一格滚多少像素
     */
    public void scrollBy(double wheelSteps, int step) {
        scrollTo(target + (int) Math.round(wheelSteps * step));
    }

    /** 把第 index 项滚进可视区，用于键盘翻页一类需要保证某项可见的场合。 */
    public void reveal(int index, int itemHeight) {
        int top = index * itemHeight;
        int bottom = top + itemHeight;
        if(top < target) scrollTo(top);
        else if(bottom > target + viewportHeight) scrollTo(bottom - viewportHeight);
    }

    /** 每帧调用一次，把当前偏移推向目标；同时推进滚动条的显示状态。 */
    public void tick() {
        float delta = UiTheme.delta();
        float previous = position;
        // 指数逼近。这里不能用 UiTheme.approach——它的步长是 delta/speed 个**像素**，
        // 换算下来每秒只滚 1/speed 像素，滚一格要几十秒。
        float factor = 1F - (float) Math.exp(-delta / FOLLOW_SECONDS);
        position += (target - position) * factor;
        if(Math.abs(position - target) < 0.25F) position = target;
        boolean moving = Math.abs(position - previous) > 0.01F;
        idleSeconds = moving ? 0F : idleSeconds + delta;
        float want = scrollable() && idleSeconds < INDICATOR_HOLD_SECONDS ? 1F : 0F;
        indicatorAmount = UiTheme.approach(indicatorAmount, want, INDICATOR_FADE_SECONDS, delta);
    }

    public boolean indicatorVisible() {
        return indicatorAmount > 0.02F;
    }

    /** 在给定轨道上画滚动条。track 只需要给出右边界和纵向范围。 */
    public void renderIndicator(GuiGraphics graphics, UiLayout.Rect track) {
        if(!indicatorVisible() || track.isEmpty() || !scrollable()) return;
        float ratio = viewportHeight / (float) contentHeight;
        float thumbHeight = Mth.clamp(track.height() * ratio, 12F, track.height());
        float travel = track.height() - thumbHeight;
        float progress = maxOffset() <= 0 ? 0F : Mth.clamp(position / maxOffset(), 0F, 1F);
        UiDraw.roundedRect(graphics, track.right() - 3F, track.y() + travel * progress, 3F, thumbHeight, 1.5F,
                UiTheme.alpha(UiTheme.ACCENT, 0.65F * indicatorAmount));
    }

    private int maxOffset() {
        return Math.max(0, contentHeight - viewportHeight);
    }
}
