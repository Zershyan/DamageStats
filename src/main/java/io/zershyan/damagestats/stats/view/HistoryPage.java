package io.zershyan.damagestats.stats.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** 当前玩家已结束战斗的双向摘要页，不包含原始伤害事件或进行中的会话。 */
public record HistoryPage(int requestId, List<SessionView> outgoing, List<SessionView> incoming) {
    private static final StreamCodec<RegistryFriendlyByteBuf, List<SessionView>> LIST_CODEC =
            SessionView.STREAM_CODEC.apply(ByteBufCodecs.list(100));

    public static final StreamCodec<RegistryFriendlyByteBuf, HistoryPage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HistoryPage::requestId,
            LIST_CODEC, HistoryPage::outgoing,
            LIST_CODEC, HistoryPage::incoming,
            HistoryPage::new
    );
}
