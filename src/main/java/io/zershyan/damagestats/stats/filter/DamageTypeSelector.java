package io.zershyan.damagestats.stats.filter;

import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
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

    StreamCodec<FriendlyByteBuf, DamageTypeSelector> STREAM_CODEC = StreamCodec.of(
            (buf, selector) -> {
                if(selector instanceof Category category) {
                    buf.writeBoolean(true);
                    ByteBufCodecs.STRING_UTF8.encode(buf, category.name());
                } else if(selector instanceof Exact exact) {
                    buf.writeBoolean(false);
                    ByteBufCodecs.RESOURCE_LOCATION.encode(buf, exact.id());
                } else {
                    throw new IllegalArgumentException("???????????" + selector);
                }
            },
            buf -> buf.readBoolean()
                    ? new Category(ByteBufCodecs.STRING_UTF8.decode(buf))
                    : new Exact(ByteBufCodecs.RESOURCE_LOCATION.decode(buf))
    );
}
