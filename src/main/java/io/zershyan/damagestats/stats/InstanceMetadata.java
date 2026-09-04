package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** 实例目录只保存识别和定位所需的最后交互信息，不承担伤害明细的存储职责。 */
public record InstanceMetadata(
        EntityRef ref,
        String displayName,
        ResourceLocation dimensionId,
        int blockX,
        int blockY,
        int blockZ,
        long lastInteractionMillis,
        long lastInteractionGameTime
) {
    public static final Codec<InstanceMetadata> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityRef.CODEC.fieldOf("ref").forGetter(InstanceMetadata::ref),
            Codec.STRING.optionalFieldOf("name", "").forGetter(InstanceMetadata::displayName),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(InstanceMetadata::dimensionId),
            Codec.INT.fieldOf("x").forGetter(InstanceMetadata::blockX),
            Codec.INT.fieldOf("y").forGetter(InstanceMetadata::blockY),
            Codec.INT.fieldOf("z").forGetter(InstanceMetadata::blockZ),
            Codec.LONG.fieldOf("lastInteraction").forGetter(InstanceMetadata::lastInteractionMillis),
            Codec.LONG.optionalFieldOf("lastInteractionGameTime", 0L).forGetter(InstanceMetadata::lastInteractionGameTime)
    ).apply(instance, InstanceMetadata::new));

    public static InstanceMetadata from(LivingEntity entity, long nowMillis) {
        BlockPos position = entity.blockPosition();
        return new InstanceMetadata(
                EntityRef.of(entity),
                entity.getName().getString(),
                entity.level().dimension().location(),
                position.getX(),
                position.getY(),
                position.getZ(),
                nowMillis,
                entity.level().getGameTime());
    }
}
