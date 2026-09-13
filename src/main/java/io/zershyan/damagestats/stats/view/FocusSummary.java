package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.focus.FocusScopeView;
import net.minecraft.network.FriendlyByteBuf;

/** 焦点页头与 Overlay 共用的摘要，页间图表不随它自动刷新。 */
public record FocusSummary(
        long revision,
        FocusScopeView scope,
        FocusMetricsView session,
        FocusMetricsView lifetime,
        boolean active,
        String worldId
) {
    public static final StreamCodec<FriendlyByteBuf, FocusSummary> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, FocusSummary::revision,
            FocusScopeView.STREAM_CODEC, FocusSummary::scope,
            FocusMetricsView.STREAM_CODEC, FocusSummary::session,
            FocusMetricsView.STREAM_CODEC, FocusSummary::lifetime,
            ByteBufCodecs.BOOL, FocusSummary::active,
            ByteBufCodecs.STRING_UTF8, FocusSummary::worldId,
            FocusSummary::new
    );

    public static FocusSummary empty() {
        return new FocusSummary(0, new FocusScopeView(0, java.util.Optional.empty(), java.util.Optional.empty(), false,
                net.minecraft.network.chat.Component.empty(), net.minecraft.network.chat.Component.empty()),
                FocusMetricsView.EMPTY, FocusMetricsView.EMPTY, false, "");
    }

    public FocusSummary withRevision(long newRevision) {
        return new FocusSummary(newRevision, scope, session, lifetime, active, worldId);
    }
}
