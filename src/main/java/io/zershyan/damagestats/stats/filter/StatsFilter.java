package io.zershyan.damagestats.stats.filter;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.DamageRecord;
import io.zershyan.damagestats.stats.EntityRef;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * 一次筛选查询。四个槽位都是空的表示不限，于是需求里那三种组合都落在同一套逻辑上：
 * 只填目标 = 这个目标受到的全部伤害；只填来源 = 这个来源打出的全部伤害；两个都填 = 来源对目标的伤害。
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record StatsFilter(
        Optional<EntitySelector> source,
        Optional<EntitySelector> target,
        Optional<EntitySelector> directSource,
        Optional<DamageTypeSelector> damageType,
        boolean sourceIsDirectSource
) {
    private static final ResourceLocation PLAYER_TYPE = new ResourceLocation("minecraft", "player");

    public StatsFilter {
        if(sourceIsDirectSource && source.filter(StatsFilter::isPlayerSelector).isPresent()) {
            sourceIsDirectSource = false;
        }
    }

    public static final StatsFilter NONE =
            new StatsFilter(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false);

    public StatsFilter(Optional<EntitySelector> source, Optional<EntitySelector> target,
                       Optional<EntitySelector> directSource, Optional<DamageTypeSelector> damageType) {
        this(source, target, directSource, damageType, false);
    }

    /** 只看这个来源打出的伤害，目标不限 */
    public static StatsFilter fromSource(EntitySelector selector) {
        return new StatsFilter(Optional.of(selector), Optional.empty(), Optional.empty(), Optional.empty(), false);
    }

    public static final StreamCodec<FriendlyByteBuf, StatsFilter> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(EntitySelector.STREAM_CODEC), StatsFilter::source,
            ByteBufCodecs.optional(EntitySelector.STREAM_CODEC), StatsFilter::target,
            ByteBufCodecs.optional(EntitySelector.STREAM_CODEC), StatsFilter::directSource,
            ByteBufCodecs.optional(DamageTypeSelector.STREAM_CODEC), StatsFilter::damageType,
            ByteBufCodecs.BOOL, StatsFilter::sourceIsDirectSource,
            StatsFilter::new
    );

    public boolean matches(DamageRecord record) {
        return matchesEntity(source, sourceIsDirectSource ? record.directSource() : record.source())
                && matchesEntity(target, record.target())
                && matchesEntity(directSource, record.directSource())
                && damageType.map(selector -> selector.matches(record.damageTypeId())).orElse(true);
    }

    /** 再点一次同一行就把这个槽清空，所以「点击隐藏其他伤害」和「取消隐藏」是同一个操作 */
    public StatsFilter toggle(FilterKey key) {
        if(key instanceof FilterKey.Source sourceKey) {
            return new StatsFilter(toggled(source, sourceKey.selector()), target, directSource, damageType, false);
        }
        if(key instanceof FilterKey.Target targetKey) {
            return new StatsFilter(source, toggled(target, targetKey.selector()), directSource, damageType,
                    sourceIsDirectSource);
        }
        if(key instanceof FilterKey.Direct directKey) {
            return new StatsFilter(source, target, toggled(directSource, directKey.selector()), damageType,
                    sourceIsDirectSource);
        }
        if(key instanceof FilterKey.Type typeKey) {
            return new StatsFilter(source, target, directSource, toggled(damageType, typeKey.selector()),
                    sourceIsDirectSource);
        }
        throw new IllegalArgumentException("?????????" + key);
    }

    public StatsFilter withSource(Optional<EntitySelector> newSource) {
        return new StatsFilter(newSource, target, directSource, damageType, false);
    }

    public StatsFilter withTarget(Optional<EntitySelector> newTarget) {
        return new StatsFilter(source, newTarget, directSource, damageType, sourceIsDirectSource);
    }

    private static boolean matchesEntity(Optional<EntitySelector> selector, EntityRef candidate) {
        return selector.map(value -> value.matches(candidate)).orElse(true);
    }

    private static boolean isPlayerSelector(EntitySelector selector) {
        if(selector instanceof EntitySelector.Instance instance) {
            return PLAYER_TYPE.equals(instance.ref().typeId());
        }
        if(selector instanceof EntitySelector.Type type) {
            return PLAYER_TYPE.equals(type.typeId());
        }
        return false;
    }

    private static <T> Optional<T> toggled(Optional<T> current, T clicked) {
        return current.filter(clicked::equals).isPresent() ? Optional.empty() : Optional.of(clicked);
    }
}
