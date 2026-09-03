package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.stats.*;
import io.zershyan.damagestats.util.DamageResolver;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 伤害采集。挂在 {@link LivingDamageEvent.Post} 上是因为只有这个时机同时拿得到
 * 原始伤害、真正扣掉的血量、盾牌格挡量和六种减免明细，而且它的数值已经定稿、不会再被别的 mod 改。
 * 代价是完全免疫（最终伤害为 0）的攻击不会触发这个事件，因此不计入统计。
 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class DamageCollectHandler {
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if(!DSConfig.TrackingEnabled.get()) return;
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return;

        LivingEntity target = event.getEntity();
        if(target.level().isClientSide) return;

        DamageSource source = event.getSource();
        tracker.record(new DamageRecord(
                DamageResolver.resolveSource(source),
                DamageResolver.resolveDirectSource(source),
                EntityRef.of(target),
                DamageResolver.resolveDamageTypeId(source),
                event.getOriginalDamage(),
                event.getNewDamage(),
                event.getBlockedDamage(),
                DamageReduction.from(event),
                target.level().getGameTime(),
                target.isDeadOrDying()
        ));
        cacheNames(tracker, source, target);
    }

    private static void cacheNames(DamageTracker tracker, DamageSource source, LivingEntity target) {
        tracker.cacheName(target);
        cacheIfPresent(tracker, DamageResolver.resolveSourceEntity(source));
        cacheIfPresent(tracker, source.getDirectEntity());
    }

    private static void cacheIfPresent(DamageTracker tracker, @Nullable Entity entity) {
        if(entity != null) tracker.cacheName(entity);
    }
}
