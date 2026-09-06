package io.zershyan.damagestats.stats.filter;

import io.netty.buffer.ByteBuf;
import io.zershyan.damagestats.stats.EntityRef;
import net.minecraft.network.codec.StreamCodec;
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

    StreamCodec<ByteBuf, EntitySelector> STREAM_CODEC = StreamCodec.of(
            (buf, selector) -> {
                switch (selector) {
                    case Instance instance -> {
                        buf.writeBoolean(true);
                        EntityRef.STREAM_CODEC.encode(buf, instance.ref());
                    }
                    case Type type -> {
                        buf.writeBoolean(false);
                        ResourceLocation.STREAM_CODEC.encode(buf, type.typeId());
                    }
                }
            },
            buf -> buf.readBoolean()
                    ? new Instance(EntityRef.STREAM_CODEC.decode(buf))
                    : new Type(ResourceLocation.STREAM_CODEC.decode(buf))
    );
}
