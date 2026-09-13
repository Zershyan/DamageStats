package io.zershyan.damagestats.stats.filter;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.EntityRef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * 实体筛选条件。两种粒度：钉住某个具体实例，或者把某个实体类型下的全部实例合起来。
 * 只负责匹配，怎么显示交给 StatsNames。
 */
public sealed interface EntitySelector {
    /** 某个具体实体实例，比如「村庄里那只被我打的僵尸」 */
    record Instance(EntityRef ref) implements EntitySelector {
        @Override
        public boolean matches(EntityRef candidate) {
            return ref.equals(candidate);
        }
    }

    /** 某个实体类型下的全部实例，比如「所有僵尸」 */
    record Type(ResourceLocation typeId) implements EntitySelector {
        @Override
        public boolean matches(EntityRef candidate) {
            return typeId.equals(candidate.typeIdOrEnvironment());
        }
    }

    boolean matches(EntityRef candidate);

    StreamCodec<FriendlyByteBuf, EntitySelector> STREAM_CODEC = StreamCodec.of(
            (buf, selector) -> {
                if(selector instanceof Instance instance) {
                    buf.writeBoolean(true);
                    EntityRef.STREAM_CODEC.encode(buf, instance.ref());
                } else if(selector instanceof Type type) {
                    buf.writeBoolean(false);
                    ByteBufCodecs.RESOURCE_LOCATION.encode(buf, type.typeId());
                } else {
                    throw new IllegalArgumentException("???????????" + selector);
                }
            },
            buf -> buf.readBoolean()
                    ? new Instance(EntityRef.STREAM_CODEC.decode(buf))
                    : new Type(ByteBufCodecs.RESOURCE_LOCATION.decode(buf))
    );
}
