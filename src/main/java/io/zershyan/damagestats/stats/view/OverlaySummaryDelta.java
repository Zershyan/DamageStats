package io.zershyan.damagestats.stats.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

/** Overlay 摘要的字段级增量包，未置位的字段沿用客户端上一份摘要。 */
public record OverlaySummaryDelta(
        int changedFields,
        Component targetName,
        float totalDamage,
        float averageDps,
        float realtimeDps,
        int hitCount,
        boolean active
) {
    private static final int TARGET_NAME = 1;
    private static final int TOTAL_DAMAGE = 1 << 1;
    private static final int AVERAGE_DPS = 1 << 2;
    private static final int REALTIME_DPS = 1 << 3;
    private static final int HIT_COUNT = 1 << 4;
    private static final int ACTIVE = 1 << 5;
    private static final int ALL_FIELDS = TARGET_NAME | TOTAL_DAMAGE | AVERAGE_DPS | REALTIME_DPS | HIT_COUNT | ACTIVE;

    public static final StreamCodec<RegistryFriendlyByteBuf, OverlaySummaryDelta> STREAM_CODEC =
            StreamCodec.of(OverlaySummaryDelta::encode, OverlaySummaryDelta::decode);

    public static OverlaySummaryDelta between(OverlaySummary previous, OverlaySummary current) {
        if(previous == null) return new OverlaySummaryDelta(
                ALL_FIELDS,
                current.targetName(),
                current.totalDamage(),
                current.averageDps(),
                current.realtimeDps(),
                current.hitCount(),
                current.active());
        int fields = 0;
        if(!previous.targetName().equals(current.targetName())) fields |= TARGET_NAME;
        if(Float.compare(previous.totalDamage(), current.totalDamage()) != 0) fields |= TOTAL_DAMAGE;
        if(Float.compare(previous.averageDps(), current.averageDps()) != 0) fields |= AVERAGE_DPS;
        if(Float.compare(previous.realtimeDps(), current.realtimeDps()) != 0) fields |= REALTIME_DPS;
        if(previous.hitCount() != current.hitCount()) fields |= HIT_COUNT;
        if(previous.active() != current.active()) fields |= ACTIVE;
        return new OverlaySummaryDelta(
                fields,
                current.targetName(),
                current.totalDamage(),
                current.averageDps(),
                current.realtimeDps(),
                current.hitCount(),
                current.active());
    }

    public boolean isEmpty() {
        return changedFields == 0;
    }

    public OverlaySummary applyTo(OverlaySummary previous) {
        return new OverlaySummary(
                changed(TARGET_NAME) ? targetName : previous.targetName(),
                changed(TOTAL_DAMAGE) ? totalDamage : previous.totalDamage(),
                changed(AVERAGE_DPS) ? averageDps : previous.averageDps(),
                changed(REALTIME_DPS) ? realtimeDps : previous.realtimeDps(),
                changed(HIT_COUNT) ? hitCount : previous.hitCount(),
                changed(ACTIVE) ? active : previous.active());
    }

    private boolean changed(int field) {
        return (changedFields & field) != 0;
    }

    private static void encode(RegistryFriendlyByteBuf buf, OverlaySummaryDelta delta) {
        buf.writeVarInt(delta.changedFields);
        if(delta.changed(TARGET_NAME)) ComponentSerialization.STREAM_CODEC.encode(buf, delta.targetName);
        if(delta.changed(TOTAL_DAMAGE)) buf.writeFloat(delta.totalDamage);
        if(delta.changed(AVERAGE_DPS)) buf.writeFloat(delta.averageDps);
        if(delta.changed(REALTIME_DPS)) buf.writeFloat(delta.realtimeDps);
        if(delta.changed(HIT_COUNT)) buf.writeVarInt(delta.hitCount);
        if(delta.changed(ACTIVE)) buf.writeBoolean(delta.active);
    }

    private static OverlaySummaryDelta decode(RegistryFriendlyByteBuf buf) {
        int fields = buf.readVarInt();
        Component targetName = (fields & TARGET_NAME) == 0 ? Component.empty()
                : ComponentSerialization.STREAM_CODEC.decode(buf);
        float totalDamage = (fields & TOTAL_DAMAGE) == 0 ? 0 : buf.readFloat();
        float averageDps = (fields & AVERAGE_DPS) == 0 ? 0 : buf.readFloat();
        float realtimeDps = (fields & REALTIME_DPS) == 0 ? 0 : buf.readFloat();
        int hitCount = (fields & HIT_COUNT) == 0 ? 0 : buf.readVarInt();
        boolean active = (fields & ACTIVE) != 0 && buf.readBoolean();
        return new OverlaySummaryDelta(fields, targetName, totalDamage, averageDps, realtimeDps, hitCount, active);
    }
}
