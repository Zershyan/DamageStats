package io.zershyan.damagestats.stats.filter;

import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** 伤害类型列表可按 JSON 分类合并，也可逐个列出注册表 ID。 */
public enum DamageTypeGrouping {
    CATEGORY,
    REGISTRY;

    public static final StreamCodec<FriendlyByteBuf, DamageTypeGrouping> STREAM_CODEC = StreamCodec.of(
            (buf, grouping) -> buf.writeBoolean(grouping == CATEGORY),
            buf -> buf.readBoolean() ? CATEGORY : REGISTRY
    );

    public DamageTypeGrouping next() {
        return this == CATEGORY ? REGISTRY : CATEGORY;
    }
}
