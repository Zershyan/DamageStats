package io.zershyan.damagestats.stats;

import java.util.ArrayDeque;

/**
 * 最近若干 tick 内的伤害滑动窗口，用来算实时 DPS。
 * 只对活跃会话维护，会话结束即清空，因此内存占用被窗口长度约束住。
 */
public class DpsWindow {
    private record Hit(long gameTime, float damage) {}

    private final ArrayDeque<Hit> hits = new ArrayDeque<>();
    private float sum;

    public void accept(long gameTime, float damage) {
        hits.addLast(new Hit(gameTime, damage));
        sum += damage;
    }

    public float dps(long currentGameTime, int windowTicks) {
        prune(currentGameTime - windowTicks);
        return sum / (windowTicks / DamageAccumulator.TICKS_PER_SECOND);
    }

    public void clear() {
        hits.clear();
        sum = 0;
    }

    private void prune(long cutoff) {
        while(!hits.isEmpty() && hits.peekFirst().gameTime() < cutoff) sum -= hits.removeFirst().damage();
        // 反复加减 float 会累积误差，窗口空了就归零自愈
        if(hits.isEmpty()) sum = 0;
    }
}
