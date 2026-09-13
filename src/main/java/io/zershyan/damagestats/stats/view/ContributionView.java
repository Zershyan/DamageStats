package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.ComponentSerialization;
import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** 单个分组对焦点伤害的最高贡献，摘要只传第一名而不携带完整图表。 */
public record ContributionView(Component name, float damage, float share) {
    public static final ContributionView EMPTY = new ContributionView(Component.empty(), 0, 0);
    public static final StreamCodec<FriendlyByteBuf, ContributionView> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, ContributionView::name,
            ByteBufCodecs.FLOAT, ContributionView::damage,
            ByteBufCodecs.FLOAT, ContributionView::share,
            ContributionView::new
    );
}
