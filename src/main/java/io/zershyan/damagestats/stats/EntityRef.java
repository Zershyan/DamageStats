package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.zershyan.damagestats.DamageStats;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * 实体标识。只存标识信息，不存显示名——显示名由 {@link DamageTracker} 的名称缓存单独维护，
 * 否则实体改名会让同一个实体在聚合时被当成两个不同的对象。
 */
public record EntityRef(UUID id, @Nullable ResourceLocation typeId) {
    public static final Codec<EntityRef> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(EntityRef::id),
            ResourceLocation.CODEC.optionalFieldOf("type").forGetter(ref -> Optional.ofNullable(ref.typeId()))
    ).apply(instance, (id, type) -> new EntityRef(id, type.orElse(null))));

    /** 固定 UUID 而不是随机值，保证持久化后仍然指向同一个环境来源 */
    public static final UUID ENVIRONMENT_ID =
            UUID.nameUUIDFromBytes("damagestats:environment".getBytes(StandardCharsets.UTF_8));

    /** 摔落、岩浆、缺氧、/kill 这类没有实体来源的伤害都挂在这里，不丢弃 */
    public static final EntityRef ENVIRONMENT = new EntityRef(ENVIRONMENT_ID, null);

    /** 环境来源没有 EntityType，但按类型分组时需要一个非 null 的 key 占位 */
    public static final ResourceLocation ENVIRONMENT_TYPE = DamageStats.id("environment");

    public static EntityRef of(@Nullable Entity entity) {
        if(entity == null) return ENVIRONMENT;
        return new EntityRef(entity.getUUID(), BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    public boolean isEnvironment() {
        return ENVIRONMENT_ID.equals(id);
    }

    public ResourceLocation typeIdOrEnvironment() {
        return typeId == null ? ENVIRONMENT_TYPE : typeId;
    }
}
