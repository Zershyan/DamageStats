package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** 焦点在一个范围内的指标与最高贡献项。 */
public record FocusMetricsView(
        MetricsView metrics,
        ContributionView topDamageType,
        ContributionView topDirectSource
) {
    public static final FocusMetricsView EMPTY = new FocusMetricsView(MetricsView.EMPTY,
            ContributionView.EMPTY, ContributionView.EMPTY);
    public static final StreamCodec<FriendlyByteBuf, FocusMetricsView> STREAM_CODEC = StreamCodec.composite(
            MetricsView.STREAM_CODEC, FocusMetricsView::metrics,
            ContributionView.STREAM_CODEC, FocusMetricsView::topDamageType,
            ContributionView.STREAM_CODEC, FocusMetricsView::topDirectSource,
            FocusMetricsView::new
    );
}
