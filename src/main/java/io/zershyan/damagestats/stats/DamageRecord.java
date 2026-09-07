package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * 一次伤害结算的完整记录。
 *
 * @param source       责任来源，召唤物和投射物已经上溯到主人
 * @param directSource 直接碰到目标的那个实体，用于界面二次筛选
 * @param gameTime     服务端世界的 game tick，20 tick = 1 秒
 * @param lethal       这次伤害是否直接导致目标死亡
 * @param dimensionId  目标所在维度，用于实例选择时区分同类实体
 * @param blockX       目标最后交互位置的方块横坐标
 * @param blockY       目标最后交互位置的方块纵坐标
 * @param blockZ       目标最后交互位置的方块纵坐标
 * @param occurredAtMillis 现实时间戳，用于实例目录的保留期
 */
public record DamageRecord(
        EntityRef source,
        EntityRef directSource,
        EntityRef target,
        ResourceLocation damageTypeId,
        float originalDamage,
        float actualDamage,
        float blockedDamage,
        DamageReduction reduction,
        long gameTime,
        boolean lethal,
        ResourceLocation dimensionId,
        int blockX,
        int blockY,
        int blockZ,
        long occurredAtMillis,
        ParticipantNames names
) {
    public record ParticipantNames(String source, String directSource, String target) {
        public static final ParticipantNames EMPTY = new ParticipantNames("", "", "");

        public static final Codec<ParticipantNames> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("source", "").forGetter(ParticipantNames::source),
                Codec.STRING.optionalFieldOf("directSource", "").forGetter(ParticipantNames::directSource),
                Codec.STRING.optionalFieldOf("target", "").forGetter(ParticipantNames::target)
        ).apply(instance, ParticipantNames::new));
    }

    public static final Codec<DamageRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            EntityRef.CODEC.fieldOf("source").forGetter(DamageRecord::source),
            EntityRef.CODEC.fieldOf("directSource").forGetter(DamageRecord::directSource),
            EntityRef.CODEC.fieldOf("target").forGetter(DamageRecord::target),
            ResourceLocation.CODEC.fieldOf("damageType").forGetter(DamageRecord::damageTypeId),
            Codec.FLOAT.fieldOf("original").forGetter(DamageRecord::originalDamage),
            Codec.FLOAT.fieldOf("actual").forGetter(DamageRecord::actualDamage),
            Codec.FLOAT.optionalFieldOf("blocked", 0f).forGetter(DamageRecord::blockedDamage),
            DamageReduction.CODEC.optionalFieldOf("reduction", DamageReduction.NONE).forGetter(DamageRecord::reduction),
            Codec.LONG.fieldOf("gameTime").forGetter(DamageRecord::gameTime),
            Codec.BOOL.fieldOf("lethal").forGetter(DamageRecord::lethal),
            ResourceLocation.CODEC.optionalFieldOf("dimension", Level.OVERWORLD.location())
                    .forGetter(DamageRecord::dimensionId),
            Codec.INT.optionalFieldOf("x", 0).forGetter(DamageRecord::blockX),
            Codec.INT.optionalFieldOf("y", 0).forGetter(DamageRecord::blockY),
            Codec.INT.optionalFieldOf("z", 0).forGetter(DamageRecord::blockZ),
            Codec.LONG.optionalFieldOf("occurredAt", 0L).forGetter(DamageRecord::occurredAtMillis),
            ParticipantNames.CODEC.optionalFieldOf("names", ParticipantNames.EMPTY).forGetter(DamageRecord::names)
    ).apply(instance, DamageRecord::new));

    public String sourceName() {
        return names.source();
    }

    public String directSourceName() {
        return names.directSource();
    }

    public String targetName() {
        return names.target();
    }

    /**
     * 取原始与实际的差值，而不是 {@link DamageReduction#total()}：
     * 别的 mod 可能直接改最终伤害而不走 NeoForge 的减免记账，差值才是真实被吃掉的量。
     */
    public float reducedDamage() {
        return Math.max(0, originalDamage - actualDamage);
    }
}
