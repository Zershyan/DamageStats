package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.filter.FilterKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * 一个分组维度里的一行：某个伤害类型、某个直接来源，或者某个对手。
 * 每一行都带着「点它会往筛选里补哪一项」，于是客户端的点击处理只剩一句 toggle。
 */
public record GroupView(Component name, float damage, float originalDamage, int hitCount, float share, FilterKey key,
                        boolean canOpenInstances) {
    public static final StreamCodec<RegistryFriendlyByteBuf, GroupView> STREAM_CODEC =
            StreamCodec.of(GroupView::encode, GroupView::decode);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<GroupView>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());

    private static void encode(RegistryFriendlyByteBuf buf, GroupView view) {
        ComponentSerialization.STREAM_CODEC.encode(buf, view.name);
        buf.writeFloat(view.damage);
        buf.writeFloat(view.originalDamage);
        buf.writeVarInt(view.hitCount);
        buf.writeFloat(view.share);
        FilterKey.STREAM_CODEC.encode(buf, view.key);
        buf.writeBoolean(view.canOpenInstances);
    }

    private static GroupView decode(RegistryFriendlyByteBuf buf) {
        return new GroupView(
                ComponentSerialization.STREAM_CODEC.decode(buf),
                buf.readFloat(),
                buf.readFloat(),
                buf.readVarInt(),
                buf.readFloat(),
                FilterKey.STREAM_CODEC.decode(buf),
                buf.readBoolean());
    }
}
