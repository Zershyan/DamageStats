package io.zershyan.damagestats.handler.common;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.stats.*;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import io.zershyan.damagestats.util.DamageResolver;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * 伤害采集。Forge 1.20.1 的 {@link LivingDamageEvent} 在伤害完成结算后触发，
 * 但不像 NeoForge 1.21 的 {@code LivingDamageEvent.Post} 提供原始伤害、格挡量和分项减免。
 * 因此此版本使用 {@link LivingDamageEvent#getAmount()} 作为已结算伤害；无法获得的字段按兼容默认值记录。
 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class DamageCollectHandler {
    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent event) {
        if(!DSConfig.TrackingEnabled.get()) return;
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return;
        DamageEventJournal journal = ServerStats.journal();
        if(journal != null && journal.isClearing()) return;
        if(ServerLifecycleHandler.storageWritesBlocked()) return;

        LivingEntity target = event.getEntity();
        if(target.level().isClientSide) return;
        if(event.getAmount() <= 0) return;

        DamageSource source = event.getSource();
        Entity sourceEntity = DamageResolver.resolveSourceEntity(source);
        Entity directSourceEntity = source.getDirectEntity();
        long nowMillis = System.currentTimeMillis();
        DamageRecord record = new DamageRecord(
                DamageResolver.resolveSource(source),
                DamageResolver.resolveDirectSource(source),
                EntityRef.of(target),
                DamageResolver.resolveDamageTypeId(source),
                event.getAmount(),
                event.getAmount(),
                0,
                DamageReduction.from(event),
                target.level().getGameTime(),
                target.isDeadOrDying(),
                target.level().dimension().location(),
                target.getBlockX(),
                target.getBlockY(),
                target.getBlockZ(),
                nowMillis,
                new DamageRecord.ParticipantNames(
                        displayName(sourceEntity),
                        displayName(directSourceEntity),
                        displayName(target))
        );
        tracker.record(record);
        tracker.touchInstance(target, nowMillis);
        touchIfPresent(tracker, sourceEntity, nowMillis);
        if(directSourceEntity instanceof LivingEntity) {
            touchIfPresent(tracker, directSourceEntity, nowMillis);
        }
        cacheNames(tracker, target, sourceEntity,
                directSourceEntity instanceof LivingEntity ? directSourceEntity : null);
        if(journal != null) journal.append(record);
        StatsSyncHandler.onDamageRecorded(record);
    }

    private static void cacheNames(DamageTracker tracker, LivingEntity target,
                                   @Nullable Entity sourceEntity, @Nullable Entity directSourceEntity) {
        tracker.cacheName(target);
        cacheIfPresent(tracker, sourceEntity);
        cacheIfPresent(tracker, directSourceEntity);
    }

    private static void touchIfPresent(DamageTracker tracker, @Nullable Entity entity, long nowMillis) {
        if(entity != null) tracker.touchInstance(entity, nowMillis);
    }

    private static void cacheIfPresent(DamageTracker tracker, @Nullable Entity entity) {
        if(entity != null) tracker.cacheName(entity);
    }

    private static String displayName(@Nullable Entity entity) {
        if(entity == null) return "";
        if(entity instanceof net.minecraft.world.entity.player.Player player) {
            return limitName(player.getGameProfile().getName());
        }
        Component customName = entity.getCustomName();
        return customName == null ? "" : limitName(customName.getString());
    }

    /** 限制名称长度，防止超长名称占用过多网络带宽和导致 GUI 溢出 */
    private static String limitName(String name) {
        return name.length() <= 64 ? name : name.substring(0, 64);
    }
}
