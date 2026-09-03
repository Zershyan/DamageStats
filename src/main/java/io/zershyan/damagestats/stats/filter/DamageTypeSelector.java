package io.zershyan.damagestats.stats.filter;

import io.netty.buffer.ByteBuf;
import io.zershyan.damagestats.config.DamageTypeCategories;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * 伤害类型筛选条件。需求要的两种口子：按 JSON 配置里定义的分类筛（一个分类下可能有好几种类型），
 * 或者按注册表 ID 精确筛。
 */
public sealed interface DamageTypeSelector {
    /** 按 JSON 里定义的分类，比如 Magic 会同时命中 minecraft:magic 和 minecraft:indirect_magic */
    record Category(String name) implements DamageTypeSelector {
        @Override
        public boolean matches(ResourceLocation damageTypeId) {
            return name.equals(DamageTypeCategories.categoryOf(damageTypeId));
        }
    }

    /** 按注册表 ID 精确筛，没被分类过的伤害类型只能这样筛 */
    record Exact(ResourceLocation id) implements DamageTypeSelector {
        @Override
        public boolean matches(ResourceLocation damageTypeId) {
            return id.equals(damageTypeId);
        }
    }

    boolean matches(ResourceLocation damageTypeId);

    StreamCodec<ByteBuf, DamageTypeSelector> STREAM_CODEC = StreamCodec.of(
            (buf, selector) -> {
                switch (selector) {
                    case Category category -> {
                        buf.writeBoolean(true);
                        ByteBufCodecs.STRING_UTF8.encode(buf, category.name());
                    }
                    case Exact exact -> {
                        buf.writeBoolean(false);
                        ResourceLocation.STREAM_CODEC.encode(buf, exact.id());
                    }
                }
            },
            buf -> buf.readBoolean()
                    ? new Category(ByteBufCodecs.STRING_UTF8.decode(buf))
                    : new Exact(ResourceLocation.STREAM_CODEC.decode(buf))
    );
}
