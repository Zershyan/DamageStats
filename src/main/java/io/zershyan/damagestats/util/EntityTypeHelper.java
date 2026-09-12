package io.zershyan.damagestats.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import org.jetbrains.annotations.Nullable;

/** 为服务端焦点校验和实体选择器提供统一的实体类型判断。 */
public final class EntityTypeHelper {
    private EntityTypeHelper() {
    }

    /**
     * 判断注册表中的实体类型是否拥有生物属性。
     *
     * <p>NeoForge 1.21 的映射中，EntityType#getBaseClass() 擦除泛型后不能可靠地区分
     * LivingEntity；DefaultAttributes 会为原版和模组生物类型提供属性供应器。</p>
     */
    public static boolean isLivingType(@Nullable ResourceLocation typeId) {
        return typeId != null
                && BuiltInRegistries.ENTITY_TYPE.getOptional(typeId)
                .filter(DefaultAttributes::hasSupplier)
                .isPresent();
    }
}
