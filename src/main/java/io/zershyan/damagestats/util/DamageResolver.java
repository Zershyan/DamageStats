package io.zershyan.damagestats.util;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.EntityRef;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import org.jetbrains.annotations.Nullable;

/** 从 {@link DamageSource} 里解出统计需要的三个维度：责任来源、直接来源、伤害类型 */
public final class DamageResolver {
    /** 主人链的最大上溯层数，防止互相认主形成环 */
    private static final int MAX_OWNER_DEPTH = 4;

    /** Holder 不是注册表引用时（第三方 mod 直接构造的伤害类型）拿不到 ID，用这个兜底而不是丢弃记录 */
    private static final ResourceLocation UNKNOWN_TYPE = DamageStats.id("unknown");

    /**
     * 责任来源。原版已经把投射物上溯到了射手，这里再把召唤物上溯到主人，
     * 于是「狼咬人」「箭射人」都会计入主人的输出。
     */
    public static EntityRef resolveSource(DamageSource source) {
        return EntityRef.of(resolveSourceEntity(source));
    }

    /** 上溯后的责任实体本身。采集端需要它来缓存显示名，光有 {@link EntityRef} 拿不到名字 */
    public static @Nullable Entity resolveSourceEntity(DamageSource source) {
        Entity causing = source.getEntity();
        return causing == null ? null : climbToOwner(causing);
    }

    /** 直接来源，用于界面二次筛选：箭、火球、狼本体都在这一层区分 */
    public static EntityRef resolveDirectSource(DamageSource source) {
        return EntityRef.of(source.getDirectEntity());
    }

    public static ResourceLocation resolveDamageTypeId(DamageSource source) {
        return source.typeHolder().unwrapKey().map(ResourceKey::location).orElse(UNKNOWN_TYPE);
    }

    private static Entity climbToOwner(Entity entity) {
        Entity current = entity;
        for (int depth = 0; depth < MAX_OWNER_DEPTH; depth++) {
            if(!(current instanceof OwnableEntity ownable)) return current;
            LivingEntity owner = ownable.getOwner();
            if(owner == null) return current;
            current = owner;
        }
        return current;
    }

    private DamageResolver() {
    }
}
