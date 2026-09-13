package io.zershyan.damagestats.stats.filter;

import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 点某一行时往筛选条件里补的那一项。把「填哪个槽」一起带上，
 * 客户端的点击处理就只剩一句 filter.toggle(row.key())，不必自己判断点的是哪个维度。
 */
public sealed interface FilterKey {
    record Source(EntitySelector selector) implements FilterKey {}

    record Target(EntitySelector selector) implements FilterKey {}

    record Direct(EntitySelector selector) implements FilterKey {}

    record Type(DamageTypeSelector selector) implements FilterKey {}

    StreamCodec<FriendlyByteBuf, FilterKey> STREAM_CODEC = StreamCodec.of(
            (buf, key) -> {
                if(key instanceof Source source) {
                    buf.writeByte(0);
                    EntitySelector.STREAM_CODEC.encode(buf, source.selector());
                } else if(key instanceof Target target) {
                    buf.writeByte(1);
                    EntitySelector.STREAM_CODEC.encode(buf, target.selector());
                } else if(key instanceof Direct direct) {
                    buf.writeByte(2);
                    EntitySelector.STREAM_CODEC.encode(buf, direct.selector());
                } else if(key instanceof Type type) {
                    buf.writeByte(3);
                    DamageTypeSelector.STREAM_CODEC.encode(buf, type.selector());
                } else {
                    throw new IllegalArgumentException("?????????" + key);
                }
            },
            buf -> switch (buf.readByte()) {
                case 0 -> new Source(EntitySelector.STREAM_CODEC.decode(buf));
                case 1 -> new Target(EntitySelector.STREAM_CODEC.decode(buf));
                case 2 -> new Direct(EntitySelector.STREAM_CODEC.decode(buf));
                default -> new Type(DamageTypeSelector.STREAM_CODEC.decode(buf));
            }
    );
}
