package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** 实例目录只保存识别和定位所需的最后交互信息，不承担伤害明细的存储职责。 */
public record InstanceMetadata(
        long instanceId,
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
            Codec.LONG.optionalFieldOf("instanceId", 0L).forGetter(InstanceMetadata::instanceId),
            EntityRef.CODEC.fieldOf("ref").forGetter(InstanceMetadata::ref),
            Codec.STRING.optionalFieldOf("name", "").forGetter(InstanceMetadata::displayName),
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(InstanceMetadata::dimensionId),
            Codec.INT.fieldOf("x").forGetter(InstanceMetadata::blockX),
            Codec.INT.fieldOf("y").forGetter(InstanceMetadata::blockY),
            Codec.INT.fieldOf("z").forGetter(InstanceMetadata::blockZ),
            Codec.LONG.fieldOf("lastInteraction").forGetter(InstanceMetadata::lastInteractionMillis),
            Codec.LONG.optionalFieldOf("lastInteractionGameTime", 0L).forGetter(InstanceMetadata::lastInteractionGameTime)
    ).apply(instance, InstanceMetadata::new));

    public static InstanceMetadata from(Entity entity, long nowMillis) {
        BlockPos position = entity.blockPosition();
        String displayName;
        if(entity instanceof Player player) {
            displayName = player.getGameProfile().getName();
        } else {
            Component customName = entity.getCustomName();
            displayName = customName == null ? "" : customName.getString();
        }
        return new InstanceMetadata(
                0L,
                EntityRef.of(entity),
                displayName,
                entity.level().dimension().location(),
                position.getX(),
                position.getY(),
                position.getZ(),
                nowMillis,
                entity.level().getGameTime());
    }
}
