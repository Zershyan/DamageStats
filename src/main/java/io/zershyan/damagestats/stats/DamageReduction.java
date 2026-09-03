package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.damagesource.DamageContainer.Reduction;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** 一次伤害被各个环节吃掉的量，对应 NeoForge 的六种 {@link Reduction} */
public record DamageReduction(
        float armor,
        float enchantments,
        float mobEffects,
        float absorption,
        float innateResistance,
        float invulnerability
) {
    public static final DamageReduction NONE = new DamageReduction(0, 0, 0, 0, 0, 0);

    public static final Codec<DamageReduction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("armor", 0f).forGetter(DamageReduction::armor),
            Codec.FLOAT.optionalFieldOf("enchantments", 0f).forGetter(DamageReduction::enchantments),
            Codec.FLOAT.optionalFieldOf("mobEffects", 0f).forGetter(DamageReduction::mobEffects),
            Codec.FLOAT.optionalFieldOf("absorption", 0f).forGetter(DamageReduction::absorption),
            Codec.FLOAT.optionalFieldOf("innateResistance", 0f).forGetter(DamageReduction::innateResistance),
            Codec.FLOAT.optionalFieldOf("invulnerability", 0f).forGetter(DamageReduction::invulnerability)
    ).apply(instance, DamageReduction::new));

    public static DamageReduction from(LivingDamageEvent.Post event) {
        return new DamageReduction(
                event.getReduction(Reduction.ARMOR),
                event.getReduction(Reduction.ENCHANTMENTS),
                event.getReduction(Reduction.MOB_EFFECTS),
                event.getReduction(Reduction.ABSORPTION),
                event.getReduction(Reduction.INNATE_RESISTANCE),
                event.getReduction(Reduction.INVULNERABILITY)
        );
    }

    public float total() {
        return armor + enchantments + mobEffects + absorption + innateResistance + invulnerability;
    }

    public DamageReduction plus(DamageReduction other) {
        return new DamageReduction(
                armor + other.armor,
                enchantments + other.enchantments,
                mobEffects + other.mobEffects,
                absorption + other.absorption,
                innateResistance + other.innateResistance,
                invulnerability + other.invulnerability
        );
    }
}
