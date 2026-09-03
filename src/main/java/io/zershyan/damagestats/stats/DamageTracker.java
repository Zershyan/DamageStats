package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.zershyan.damagestats.config.DSConfig;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 全部统计数据的容器。实例级条目限量淘汰，按实体类型汇总的条目体量小，永久保留。
 * 同一条伤害会同时记进「来源的输出统计」和「目标的承伤统计」，冗余换来查询时不必遍历原始记录。
 */
public class DamageTracker {
    /** 玩家条目永不淘汰：玩家是最主要的统计对象，被一群小怪挤掉会让数据莫名消失 */
    private static final ResourceLocation PLAYER_TYPE = ResourceLocation.withDefaultNamespace("player");

    /** 实例条目的 key 是个对象，当不了 JSON 的键，只能存成列表 */
    private record InstanceEntry(EntityRef owner, StatsEntry stats) {
        static final Codec<InstanceEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityRef.CODEC.fieldOf("owner").forGetter(InstanceEntry::owner),
                StatsEntry.CODEC.fieldOf("stats").forGetter(InstanceEntry::stats)
        ).apply(instance, InstanceEntry::new));
    }

    /** Overlay 的目标类型是玩家的临时选择，不进存档 */
    public static final Codec<DamageTracker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            InstanceEntry.CODEC.listOf().optionalFieldOf("outgoing", List.of())
                    .forGetter(tracker -> toEntryList(tracker.outgoing)),
            InstanceEntry.CODEC.listOf().optionalFieldOf("incoming", List.of())
                    .forGetter(tracker -> toEntryList(tracker.incoming)),
            Codec.unboundedMap(ResourceLocation.CODEC, StatsEntry.CODEC)
                    .optionalFieldOf("outgoingByType", Map.of()).forGetter(tracker -> tracker.outgoingByType),
            Codec.unboundedMap(ResourceLocation.CODEC, StatsEntry.CODEC)
                    .optionalFieldOf("incomingByType", Map.of()).forGetter(tracker -> tracker.incomingByType),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)
                    .optionalFieldOf("names", Map.of()).forGetter(tracker -> tracker.nameCache),
            StatsEntry.CODEC.optionalFieldOf("global")
                    .forGetter(tracker -> Optional.of(tracker.global))
    ).apply(instance, DamageTracker::restore));

    private final Map<EntityRef, StatsEntry> outgoing = new HashMap<>();
    private final Map<EntityRef, StatsEntry> incoming = new HashMap<>();
    private final Map<ResourceLocation, StatsEntry> outgoingByType = new HashMap<>();
    private final Map<ResourceLocation, StatsEntry> incomingByType = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>();
    private StatsEntry global;

    /** Overlay 目标类型为空时表示玩家对任意目标造成的伤害 */
    private final Map<UUID, ResourceLocation> overlayTargetTypes = new HashMap<>();

    /** 带筛选条件的查询要翻这份原始记录，预聚合的分组撑不住四个槽位的任意组合 */
    private final DamageLog log = new DamageLog();

    public DamageTracker() {
        global = new StatsEntry();
    }

    public void record(DamageRecord record) {
        // 完全免疫或完全格挡的事件不算命中，也不应进入原始日志
        if(record.actualDamage() <= 0) return;
        log.accept(record, DSConfig.DamageLogLimit.get());
        entry(outgoing, record.source()).accept(record, record.target());
        entry(incoming, record.target()).accept(record, record.source());
        entry(outgoingByType, record.source().typeIdOrEnvironment()).accept(record, record.target());
        entry(incomingByType, record.target().typeIdOrEnvironment()).accept(record, record.source());
        global.accept(record, record.target());
        int limit = DSConfig.InstanceEntryLimit.get();
        evict(outgoing, limit);
        evict(incoming, limit);
    }

    public void tick(long currentGameTime) {
        int timeout = DSConfig.SessionTimeoutTicks.get();
        int keep = DSConfig.KeepFinishedSessions.get();
        tickAll(outgoing.values(), currentGameTime, timeout, keep);
        tickAll(incoming.values(), currentGameTime, timeout, keep);
        tickAll(outgoingByType.values(), currentGameTime, timeout, keep);
        tickAll(incomingByType.values(), currentGameTime, timeout, keep);
        global.tick(currentGameTime, timeout, keep);
    }

    public void reset() {
        outgoing.clear();
        incoming.clear();
        outgoingByType.clear();
        incomingByType.clear();
        global = new StatsEntry();
        log.clear();
        nameCache.clear();
    }

    public DamageLog log() {
        return log;
    }

    /** 只记玩家名和被命名过的实体：普通怪物用 EntityType 的翻译名就够，不必为每只怪存一份字符串 */
    public void cacheName(Entity entity) {
        if(entity instanceof Player player) {
            nameCache.put(entity.getUUID(), player.getGameProfile().getName());
            return;
        }
        Component custom = entity.getCustomName();
        if(custom != null) nameCache.put(entity.getUUID(), custom.getString());
    }

    public @Nullable String cachedName(UUID id) {
        return nameCache.get(id);
    }

    /** 传 null 表示 Overlay 不限制目标类型 */
    public void setOverlayTargetType(UUID playerId, @Nullable ResourceLocation targetType) {
        if(targetType == null) overlayTargetTypes.remove(playerId);
        else overlayTargetTypes.put(playerId, targetType);
    }

    public @Nullable ResourceLocation overlayTargetType(UUID playerId) {
        return overlayTargetTypes.get(playerId);
    }

    public void clearPlayerState(UUID playerId) {
        overlayTargetTypes.remove(playerId);
    }

    public @Nullable StatsEntry outgoing(EntityRef ref) {
        return outgoing.get(ref);
    }

    public @Nullable StatsEntry incoming(EntityRef ref) {
        return incoming.get(ref);
    }

    public @Nullable StatsEntry outgoingByType(ResourceLocation typeId) {
        return outgoingByType.get(typeId);
    }

    public @Nullable StatsEntry incomingByType(ResourceLocation typeId) {
        return incomingByType.get(typeId);
    }

    public StatsEntry global() {
        return global;
    }

    /** 只清这一个对象的实例条目。按类型的汇总里混着所有实体的数据，没法按单个对象剥离 */
    public void resetFor(EntityRef owner) {
        outgoing.remove(owner);
        incoming.remove(owner);
        log.markReset(owner);
    }

    private static <K> StatsEntry entry(Map<K, StatsEntry> map, K key) {
        return map.computeIfAbsent(key, k -> new StatsEntry());
    }

    private static List<InstanceEntry> toEntryList(Map<EntityRef, StatsEntry> map) {
        return map.entrySet().stream()
                .map(entry -> new InstanceEntry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static DamageTracker restore(List<InstanceEntry> outgoing, List<InstanceEntry> incoming,
                                          Map<ResourceLocation, StatsEntry> outgoingByType,
                                          Map<ResourceLocation, StatsEntry> incomingByType,
                                          Map<UUID, String> names,
                                          Optional<StatsEntry> global) {
        DamageTracker tracker = new DamageTracker();
        outgoing.forEach(entry -> tracker.outgoing.put(entry.owner(), entry.stats()));
        incoming.forEach(entry -> tracker.incoming.put(entry.owner(), entry.stats()));
        tracker.outgoingByType.putAll(outgoingByType);
        tracker.incomingByType.putAll(incomingByType);
        tracker.nameCache.putAll(names);
        if(global.isPresent()) {
            tracker.global = global.get();
        } else {
            outgoingByType.values().forEach(tracker.global::absorbLifetime);
        }
        return tracker;
    }

    private static void tickAll(Collection<StatsEntry> entries, long gameTime, int timeout, int keep) {
        entries.forEach(entry -> entry.tick(gameTime, timeout, keep));
    }

    private static void evict(Map<EntityRef, StatsEntry> map, int limit) {
        while(map.size() > limit) {
            EntityRef oldest = null;
            long oldestTime = Long.MAX_VALUE;
            for (Map.Entry<EntityRef, StatsEntry> candidate : map.entrySet()) {
                if(PLAYER_TYPE.equals(candidate.getKey().typeId())) continue;
                if(candidate.getValue().getLastActivityTime() >= oldestTime) continue;
                oldestTime = candidate.getValue().getLastActivityTime();
                oldest = candidate.getKey();
            }
            // 剩下的全是玩家条目，说明上限比在线人数还小，不再往下淘汰
            if(oldest == null) return;
            map.remove(oldest);
        }
    }
}
