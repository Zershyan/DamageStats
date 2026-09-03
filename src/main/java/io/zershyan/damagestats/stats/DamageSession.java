package io.zershyan.damagestats.stats;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 一场进行中的战斗会话。超过配置的超时时长没有新伤害进来就被归档成 {@link SessionSummary}，
 * 下一次伤害会开一场新的。
 * 除了整场的滑动窗口，还给每个对手单独维护一个窗口——Overlay 按实体类型筛选时需要合并实时 DPS。
 * 一场战斗内对手数量有限，会话被归档时整个对象一起丢掉，所以这些窗口不会累积。
 */
public class DamageSession {
    private final DamageAccumulator accumulator =
            new DamageAccumulator(DamageAccumulator.OpponentGrouping.INSTANCE);
    private final DpsWindow window = new DpsWindow();
    private final Map<EntityRef, DpsWindow> opponentWindows = new HashMap<>();
    private final long startTime;
    private long lastActivityTime;

    public DamageSession(long startTime) {
        this.startTime = startTime;
        this.lastActivityTime = startTime;
    }

    /** 按「本场」筛选原始记录时要用它当时间下界 */
    public long getStartTime() {
        return startTime;
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

    public DamageAccumulator getAccumulator() {
        return accumulator;
    }

    /** 把本场同一实体类型的多个目标合并，供 Overlay 的类型筛选使用 */
    public @Nullable DamageAccumulator getOpponentTypeGroup(ResourceLocation typeId) {
        DamageAccumulator result = null;
        for (Map.Entry<EntityRef, DamageAccumulator> entry : accumulator.getByOpponent().entrySet()) {
            if(!typeId.equals(entry.getKey().typeIdOrEnvironment())) continue;
            if(result == null) result = new DamageAccumulator(DamageAccumulator.OpponentGrouping.NONE);
            result.absorb(entry.getValue());
        }
        return result;
    }

    /** 类型筛选的实时 DPS，不能把不同目标各自的窗口混成一个目标窗口 */
    public float getOpponentTypeRealtimeDps(ResourceLocation typeId, long currentGameTime, int windowTicks) {
        float total = 0;
        for (Map.Entry<EntityRef, DpsWindow> entry : opponentWindows.entrySet()) {
            if(typeId.equals(entry.getKey().typeIdOrEnvironment())) {
                total += entry.getValue().dps(currentGameTime, windowTicks);
            }
        }
        return total;
    }
}
