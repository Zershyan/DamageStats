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

    /** 锁定目标和最近目标是玩家的临时状态，不进存档 */
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
                    .optionalFieldOf("names", Map.of()).forGetter(tracker -> tracker.nameCache)
    ).apply(instance, DamageTracker::restore));

    private final Map<EntityRef, StatsEntry> outgoing = new HashMap<>();
    private final Map<EntityRef, StatsEntry> incoming = new HashMap<>();
    private final Map<ResourceLocation, StatsEntry> outgoingByType = new HashMap<>();
    private final Map<ResourceLocation, StatsEntry> incomingByType = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>();

    /** 玩家准星锁定的 Overlay 常显目标 */
    private final Map<UUID, EntityRef> lockedTargets = new HashMap<>();

    /** 玩家最近打的目标，未锁定时 Overlay 显示它 */
    private final Map<UUID, EntityRef> recentTargets = new HashMap<>();

    public void record(DamageRecord record) {
        entry(outgoing, record.source()).accept(record, record.target());
        entry(incoming, record.target()).accept(record, record.source());
        entry(outgoingByType, record.source().typeIdOrEnvironment()).accept(record, record.target());
        entry(incomingByType, record.target().typeIdOrEnvironment()).accept(record, record.source());
        // 只记玩家的最近目标：玩家数量有上界，换成怪物就会跟着刷怪一起堆积
        if(PLAYER_TYPE.equals(record.source().typeId())) recentTargets.put(record.source().id(), record.target());

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
    }

    public void reset() {
        outgoing.clear();
        incoming.clear();
        outgoingByType.clear();
        incomingByType.clear();
        recentTargets.clear();
        // 锁定目标是玩家自己的选择，不跟着统计数据一起清
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

    /** 传 null 表示解除锁定 */
    public void lockTarget(UUID playerId, @Nullable EntityRef target) {
        if(target == null) lockedTargets.remove(playerId);
        else lockedTargets.put(playerId, target);
    }

    public @Nullable EntityRef lockedTarget(UUID playerId) {
        return lockedTargets.get(playerId);
    }

    /** 锁定的优先，没锁定就用最近打过的 */
    public @Nullable EntityRef overlayTarget(UUID playerId) {
        EntityRef locked = lockedTargets.get(playerId);
        return locked != null ? locked : recentTargets.get(playerId);
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

    public Map<EntityRef, StatsEntry> allOutgoing() {
        return Collections.unmodifiableMap(outgoing);
    }

    public Map<EntityRef, StatsEntry> allIncoming() {
        return Collections.unmodifiableMap(incoming);
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
                                         Map<UUID, String> names) {
        DamageTracker tracker = new DamageTracker();
        outgoing.forEach(entry -> tracker.outgoing.put(entry.owner(), entry.stats()));
        incoming.forEach(entry -> tracker.incoming.put(entry.owner(), entry.stats()));
        tracker.outgoingByType.putAll(outgoingByType);
        tracker.incomingByType.putAll(incomingByType);
        tracker.nameCache.putAll(names);
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
