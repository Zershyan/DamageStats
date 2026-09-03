package io.zershyan.damagestats.stats.filter;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 点某一行时往筛选条件里补的那一项。把「填哪个槽」一起带上，
 * 客户端的点击处理就只剩一句 filter.toggle(row.key())，不必自己判断点的是哪个维度。
 */
public sealed interface FilterKey {
    record Source(EntitySelector selector) implements FilterKey {}

    record Target(EntitySelector selector) implements FilterKey {}

    record Direct(EntitySelector selector) implements FilterKey {}

    record Type(DamageTypeSelector selector) implements FilterKey {}

    StreamCodec<ByteBuf, FilterKey> STREAM_CODEC = StreamCodec.of(
            (buf, key) -> {
                switch (key) {
                    case Source(EntitySelector selector) -> {
                        buf.writeByte(0);
                        EntitySelector.STREAM_CODEC.encode(buf, selector);
                    }
                    case Target(EntitySelector selector) -> {
                        buf.writeByte(1);
                        EntitySelector.STREAM_CODEC.encode(buf, selector);
                    }
                    case Direct(EntitySelector selector) -> {
                        buf.writeByte(2);
                        EntitySelector.STREAM_CODEC.encode(buf, selector);
                    }
                    case Type(DamageTypeSelector selector) -> {
                        buf.writeByte(3);
                        DamageTypeSelector.STREAM_CODEC.encode(buf, selector);
                    }
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
