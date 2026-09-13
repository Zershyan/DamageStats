package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** 一份指标连同它的三个分组维度。会话和累计各有一份，GUI 切换时分解也跟着换 */
public record StatsView(
        MetricsView metrics,
        List<GroupView> byType,
        List<GroupView> bySource,
        List<GroupView> byOpponent
) {
    public static final StreamCodec<FriendlyByteBuf, StatsView> STREAM_CODEC = StreamCodec.composite(
            MetricsView.STREAM_CODEC, StatsView::metrics,
            GroupView.LIST_STREAM_CODEC, StatsView::byType,
            GroupView.LIST_STREAM_CODEC, StatsView::bySource,
            GroupView.LIST_STREAM_CODEC, StatsView::byOpponent,
            StatsView::new
    );

    public static final StatsView EMPTY = new StatsView(MetricsView.EMPTY, List.of(), List.of(), List.of());

    public boolean isEmpty() {
        return metrics.isEmpty();
    }
}
