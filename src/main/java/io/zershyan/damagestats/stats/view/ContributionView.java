package io.zershyan.damagestats.stats.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 单个分组对焦点伤害的最高贡献，摘要只传第一名而不携带完整图表。 */
public record ContributionView(Component name, float damage, float share) {
    public static final ContributionView EMPTY = new ContributionView(Component.empty(), 0, 0);
    public static final StreamCodec<RegistryFriendlyByteBuf, ContributionView> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, ContributionView::name,
            ByteBufCodecs.FLOAT, ContributionView::damage,
            ByteBufCodecs.FLOAT, ContributionView::share,
            ContributionView::new
    );
}
