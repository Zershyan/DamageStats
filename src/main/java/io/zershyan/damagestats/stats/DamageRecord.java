package io.zershyan.damagestats.stats;

import net.minecraft.resources.ResourceLocation;

/**
 * 一次伤害结算的完整记录。
 *
 * @param source       责任来源，召唤物和投射物已经上溯到主人
 * @param directSource 直接碰到目标的那个实体，用于界面二次筛选
 * @param gameTime     服务端世界的 game tick，20 tick = 1 秒
 * @param lethal       这次伤害是否直接导致目标死亡
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
        boolean lethal
) {
    /**
     * 取原始与实际的差值，而不是 {@link DamageReduction#total()}：
     * 别的 mod 可能直接改最终伤害而不走 NeoForge 的减免记账，差值才是真实被吃掉的量。
     */
    public float reducedDamage() {
        return Math.max(0, originalDamage - actualDamage);
    }
}
