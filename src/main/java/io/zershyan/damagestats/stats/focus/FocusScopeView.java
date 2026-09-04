package io.zershyan.damagestats.stats.focus;

import io.zershyan.damagestats.stats.filter.EntitySelector;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Optional;

/** 供客户端显示和比对的焦点范围，不携带任何客户端可伪造的授权结论。 */
public record FocusScopeView(
        long version,
        Optional<EntitySelector> source,
        Optional<EntitySelector> target,
        boolean sourceIsDirectSource,
        Component sourceName,
        Component targetName
) {
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusScopeView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, FocusScopeView::version,
            ByteBufCodecs.optional(EntitySelector.STREAM_CODEC), FocusScopeView::source,
            ByteBufCodecs.optional(EntitySelector.STREAM_CODEC), FocusScopeView::target,
            ByteBufCodecs.BOOL, FocusScopeView::sourceIsDirectSource,
            ComponentSerialization.STREAM_CODEC, FocusScopeView::sourceName,
            ComponentSerialization.STREAM_CODEC, FocusScopeView::targetName,
            FocusScopeView::new
    );
}
