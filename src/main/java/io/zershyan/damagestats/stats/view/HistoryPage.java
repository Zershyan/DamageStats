package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** 当前筛选条件下已结束战斗的摘要页，不包含原始伤害事件或进行中的会话。 */
public record HistoryPage(int requestId, boolean allowed,
                          List<SessionView> outgoing, List<SessionView> incoming) {
    private static final StreamCodec<FriendlyByteBuf, List<SessionView>> LIST_CODEC =
            SessionView.STREAM_CODEC.apply(ByteBufCodecs.list(100));

    public static final StreamCodec<FriendlyByteBuf, HistoryPage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HistoryPage::requestId,
            ByteBufCodecs.BOOL, HistoryPage::allowed,
            LIST_CODEC, HistoryPage::outgoing,
            LIST_CODEC, HistoryPage::incoming,
            HistoryPage::new
    );
}
