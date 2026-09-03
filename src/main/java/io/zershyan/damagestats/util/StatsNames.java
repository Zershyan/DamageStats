package io.zershyan.damagestats.util;

import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * 把统计里的各种标识翻成给人看的名字。返回 Component 而不是 String，
 * 这样经网络传给客户端后仍然按客户端的语言渲染。
 */
public final class StatsNames {
    /** 归了类就显示分类的译名，没归类就是原始注册表 ID */
    public static Component damageType(@Nullable ResourceLocation damageTypeId) {
        return damageTypeId == null ? Component.literal("-") : DamageTypeCategories.displayName(damageTypeId);
    }

    /** 玩家名和被命名过的实体走缓存，其余用 EntityType 的译名——实体死了之后照样显示得出来 */
    public static Component opponent(DamageTracker tracker, EntityRef ref) {
        if(ref.isEnvironment()) return DSKeyLang.SourceEnvironment.copy();
        String cached = tracker.cachedName(ref.id());
        if(cached != null) return Component.literal(cached);
        return entityType(ref.typeIdOrEnvironment());
    }

    public static Component entityType(ResourceLocation entityTypeId) {
        if(EntityRef.ENVIRONMENT_TYPE.equals(entityTypeId)) return DSKeyLang.SourceEnvironment.copy();
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId)
                .map(EntityType::getDescription)
                .orElse(Component.literal(entityTypeId.toString()));
    }
}
