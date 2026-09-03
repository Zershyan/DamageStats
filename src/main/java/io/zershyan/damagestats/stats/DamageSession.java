package io.zershyan.damagestats.stats;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 一场战斗会话。超过配置的超时时长没有新伤害进来就结束，下一次伤害会开一场新的。
 * 除了整场的滑动窗口，还给每个对手单独维护一个窗口——Overlay 盯着某个目标时需要「对它的实时 DPS」。
 * 一场战斗内对手数量有限，会话结束时全部清掉，所以这些窗口不会累积。
 */
public class DamageSession {
    private final DamageAccumulator accumulator = new DamageAccumulator();
    private final DpsWindow window = new DpsWindow();
    private final Map<EntityRef, DpsWindow> opponentWindows = new HashMap<>();
    private final long startTime;
    private long lastActivityTime;
    private boolean finished;

    public DamageSession(long startTime) {
        this.startTime = startTime;
        this.lastActivityTime = startTime;
    }

    public void accept(DamageRecord record, EntityRef opponent) {
        accumulator.accept(record, opponent);
        window.accept(record.gameTime(), record.actualDamage());
        opponentWindows.computeIfAbsent(opponent, key -> new DpsWindow())
                .accept(record.gameTime(), record.actualDamage());
        lastActivityTime = record.gameTime();
    }

    public boolean isTimedOut(long currentGameTime, int timeoutTicks) {
        return currentGameTime - lastActivityTime > timeoutTicks;
    }

    public void finish() {
        finished = true;
        // 会话结束后实时 DPS 没有意义，窗口里的命中记录可以直接扔掉
        window.clear();
        opponentWindows.clear();
    }

    /** 已结束的会话固定返回 0，避免界面上显示一个永远不动的历史瞬时值 */
    public float getRealtimeDps(long currentGameTime, int windowTicks) {
        return finished ? 0 : window.dps(currentGameTime, windowTicks);
    }

    public float getOpponentRealtimeDps(EntityRef opponent, long currentGameTime, int windowTicks) {
        if(finished) return 0;
        DpsWindow opponentWindow = opponentWindows.get(opponent);
        return opponentWindow == null ? 0 : opponentWindow.dps(currentGameTime, windowTicks);
    }

    public DamageAccumulator getAccumulator() { return accumulator; }
    public long getStartTime() { return startTime; }
    public long getLastActivityTime() { return lastActivityTime; }
    public boolean isFinished() { return finished; }

    public @Nullable DamageAccumulator getOpponentGroup(EntityRef opponent) {
        return accumulator.getByOpponent().get(opponent);
    }
}
