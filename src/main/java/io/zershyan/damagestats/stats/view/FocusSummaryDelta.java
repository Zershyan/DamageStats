package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.focus.FocusScopeView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** 同 tick 内合并后的焦点摘要增量，只编码与上一份不同的字段。 */
public record FocusSummaryDelta(
        int changedFields,
        FocusSummary summary
) {
    private static final int SCOPE = 1;
    private static final int SESSION = 1 << 1;
    private static final int LIFETIME = 1 << 2;
    private static final int ACTIVE = 1 << 3;
    private static final int WORLD = 1 << 4;
    private static final int ALL_FIELDS = SCOPE | SESSION | LIFETIME | ACTIVE | WORLD;

    public static final StreamCodec<RegistryFriendlyByteBuf, FocusSummaryDelta> STREAM_CODEC =
            StreamCodec.of(FocusSummaryDelta::encode, FocusSummaryDelta::decode);

    public static FocusSummaryDelta between(FocusSummary previous, FocusSummary current) {
        if(previous == null) return new FocusSummaryDelta(ALL_FIELDS, current);
        int fields = 0;
        if(!previous.scope().equals(current.scope())) fields |= SCOPE;
        if(!previous.session().equals(current.session())) fields |= SESSION;
        if(!previous.lifetime().equals(current.lifetime())) fields |= LIFETIME;
        if(previous.active() != current.active()) fields |= ACTIVE;
        if(!previous.worldId().equals(current.worldId())) fields |= WORLD;
        return new FocusSummaryDelta(fields, current);
    }

    public boolean isEmpty() {
        return changedFields == 0;
    }

    public FocusSummary applyTo(FocusSummary previous) {
        return new FocusSummary(
                summary.revision(),
                changed(SCOPE) ? summary.scope() : previous.scope(),
                changed(SESSION) ? summary.session() : previous.session(),
                changed(LIFETIME) ? summary.lifetime() : previous.lifetime(),
                changed(ACTIVE) ? summary.active() : previous.active(),
                changed(WORLD) ? summary.worldId() : previous.worldId());
    }

    private boolean changed(int field) {
        return (changedFields & field) != 0;
    }

    private static void encode(RegistryFriendlyByteBuf buf, FocusSummaryDelta delta) {
        buf.writeVarLong(delta.summary.revision());
        buf.writeVarInt(delta.changedFields);
        if(delta.changed(SCOPE)) FocusScopeView.STREAM_CODEC.encode(buf, delta.summary.scope());
        if(delta.changed(SESSION)) FocusMetricsView.STREAM_CODEC.encode(buf, delta.summary.session());
        if(delta.changed(LIFETIME)) FocusMetricsView.STREAM_CODEC.encode(buf, delta.summary.lifetime());
        if(delta.changed(ACTIVE)) buf.writeBoolean(delta.summary.active());
        if(delta.changed(WORLD)) buf.writeUtf(delta.summary.worldId(), 128);
    }

    private static FocusSummaryDelta decode(RegistryFriendlyByteBuf buf) {
        long revision = buf.readVarLong();
        int fields = buf.readVarInt();
        FocusScopeView scope = (fields & SCOPE) == 0 ? FocusSummary.empty().scope()
                : FocusScopeView.STREAM_CODEC.decode(buf);
        FocusMetricsView session = (fields & SESSION) == 0 ? FocusMetricsView.EMPTY
                : FocusMetricsView.STREAM_CODEC.decode(buf);
        FocusMetricsView lifetime = (fields & LIFETIME) == 0 ? FocusMetricsView.EMPTY
                : FocusMetricsView.STREAM_CODEC.decode(buf);
        boolean active = (fields & ACTIVE) != 0 && buf.readBoolean();
        String worldId = (fields & WORLD) == 0 ? "" : buf.readUtf(128);
        return new FocusSummaryDelta(fields, new FocusSummary(revision, scope, session, lifetime, active, worldId));
    }
}
