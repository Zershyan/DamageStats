package io.zershyan.damagestats.stats.view;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** 一个分组维度里的一行：某个伤害类型、某个直接来源，或者某个对手 */
public record GroupView(Component name, float damage, int hitCount, float share) {
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupView> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, GroupView::name,
            ByteBufCodecs.FLOAT, GroupView::damage,
            ByteBufCodecs.VAR_INT, GroupView::hitCount,
            ByteBufCodecs.FLOAT, GroupView::share,
            GroupView::new
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, List<GroupView>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());
}
