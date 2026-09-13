package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.ComponentSerialization;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

/** 服务器筛选、排序后的一个实体类型或实例候选。 */
public record EntityChoiceView(EntitySelector selector, Component name, Component detail) {
    public static final StreamCodec<FriendlyByteBuf, EntityChoiceView> STREAM_CODEC = StreamCodec.composite(
            EntitySelector.STREAM_CODEC, EntityChoiceView::selector,
            ComponentSerialization.STREAM_CODEC, EntityChoiceView::name,
            ComponentSerialization.STREAM_CODEC, EntityChoiceView::detail,
            EntityChoiceView::new
    );
}
