package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

/** 一次伤害的各类减免量。Forge 1.20.1 无 NeoForge 的分项 Reduction 数据，因此兼容实现缺失项按 0 记录。 */
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

    public static final StreamCodec<FriendlyByteBuf, DamageReduction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, DamageReduction::armor,
            ByteBufCodecs.FLOAT, DamageReduction::enchantments,
            ByteBufCodecs.FLOAT, DamageReduction::mobEffects,
            ByteBufCodecs.FLOAT, DamageReduction::absorption,
            ByteBufCodecs.FLOAT, DamageReduction::innateResistance,
            ByteBufCodecs.FLOAT, DamageReduction::invulnerability,
            DamageReduction::new
    );

    public static DamageReduction from(LivingDamageEvent event) {
        return NONE;
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
