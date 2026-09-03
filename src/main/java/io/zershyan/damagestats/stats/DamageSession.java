package io.zershyan.damagestats.stats;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 一场进行中的战斗会话。超过配置的超时时长没有新伤害进来就被归档成 {@link SessionSummary}，
 * 下一次伤害会开一场新的。
 * 除了整场的滑动窗口，还给每个对手单独维护一个窗口——Overlay 盯着某个目标时需要「对它的实时 DPS」。
 * 一场战斗内对手数量有限，会话被归档时整个对象一起丢掉，所以这些窗口不会累积。
 */
public class DamageSession {
    private final DamageAccumulator accumulator = new DamageAccumulator();
    private final DpsWindow window = new DpsWindow();
    private final Map<EntityRef, DpsWindow> opponentWindows = new HashMap<>();
    private long lastActivityTime;

    public DamageSession(long startTime) {
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

    public SessionSummary summarize() {
        return new SessionSummary(accumulator.getTotalActual(), accumulator.getAverageDps(),
                accumulator.getDurationTicks(), accumulator.getHitCount());
    }

    public float getRealtimeDps(long currentGameTime, int windowTicks) {
        return window.dps(currentGameTime, windowTicks);
    }

    public float getOpponentRealtimeDps(EntityRef opponent, long currentGameTime, int windowTicks) {
        DpsWindow opponentWindow = opponentWindows.get(opponent);
        return opponentWindow == null ? 0 : opponentWindow.dps(currentGameTime, windowTicks);
    }

    public DamageAccumulator getAccumulator() {
        return accumulator;
    }

    public @Nullable DamageAccumulator getOpponentGroup(EntityRef opponent) {
        return accumulator.getByOpponent().get(opponent);
    }
}
