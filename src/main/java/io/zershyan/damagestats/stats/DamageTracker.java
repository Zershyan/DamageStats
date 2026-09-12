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

    public record ReplayRecord(long sequence, DamageRecord record) {}

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
                    .forGetter(tracker -> Optional.of(tracker.global)),
            InstanceDirectory.CODEC.optionalFieldOf("instanceDirectory", new InstanceDirectory())
                    .forGetter(DamageTracker::instanceDirectory)
    ).apply(instance, DamageTracker::restore));

    private final Map<EntityRef, StatsEntry> outgoing = new HashMap<>();
    private final Map<EntityRef, StatsEntry> incoming = new HashMap<>();
    private final Map<ResourceLocation, StatsEntry> outgoingByType = new HashMap<>();
    private final Map<ResourceLocation, StatsEntry> incomingByType = new HashMap<>();
    private final Map<UUID, String> nameCache = new HashMap<>();
    private final InstanceDirectory instanceDirectory = new InstanceDirectory();
    private StatsEntry global;

    public DamageTracker() {
        global = new StatsEntry();
    }

    /** 聚合缓存损坏时回放完整事件，恢复实例候选、名称缓存和快速聚合。 */
    public static DamageTracker rebuildFromRecords(Collection<DamageRecord> records, long currentGameTime) {
        List<ReplayRecord> replay = new ArrayList<>(records.size());
        long sequence = 0;
        for(DamageRecord record : records) replay.add(new ReplayRecord(sequence++, record));
        return rebuildFromSequencedRecords(replay, Map.of(), currentGameTime);
    }

    public static DamageTracker rebuildFromSequencedRecords(Collection<ReplayRecord> records,
                                                            Map<UUID, Long> resetSequences,
                                                            long currentGameTime) {
        DamageTracker tracker = new DamageTracker();
        records.stream()
                .sorted(Comparator.comparingLong((ReplayRecord entry) -> entry.record().gameTime())
                        .thenComparingLong(ReplayRecord::sequence))
                .forEach(entry -> {
                    DamageRecord record = entry.record();
                    if(record.actualDamage() <= 0) return;
                    boolean includeOutgoing = !clearedBefore(record.source().id(), entry.sequence(), resetSequences);
                    boolean includeIncoming = !clearedBefore(record.target().id(), entry.sequence(), resetSequences);
                    tracker.record(record, includeOutgoing, includeIncoming);
                    tracker.recoverName(record.target(), record.targetName());
                    tracker.recoverName(record.source(), record.sourceName());
                    tracker.recoverName(record.directSource(), record.directSourceName());
                    tracker.instanceDirectory.touchFromRecord(record.target(), record, record.targetName());
                    tracker.instanceDirectory.touchFromRecord(record.source(), record, record.sourceName());
                    tracker.instanceDirectory.touchFromRecord(record.directSource(), record,
                            record.directSourceName());
                });
        tracker.pruneInstanceDirectory(System.currentTimeMillis());
        tracker.tick(currentGameTime);
        return tracker;
    }

    public void record(DamageRecord record) {
        record(record, true, true);
    }

    private void record(DamageRecord record, boolean includeOutgoing, boolean includeIncoming) {
        // 完全免疫或完全格挡的事件不算命中
        if(record.actualDamage() <= 0) return;
        if(includeOutgoing) entry(outgoing, record.source()).accept(record, record.target());
        if(includeIncoming) entry(incoming, record.target()).accept(record, record.source());
        entry(outgoingByType, record.source().typeIdOrEnvironment()).accept(record, record.target());
        entry(incomingByType, record.target().typeIdOrEnvironment()).accept(record, record.source());
        global.accept(record, record.target());
        if(includeOutgoing || includeIncoming) {
            int limit = DSConfig.InstanceDirectoryLimit.get();
            if(includeOutgoing) evict(outgoing, limit);
            if(includeIncoming) evict(incoming, limit);
        }
    }

    public boolean tick(long currentGameTime) {
        int timeout = DSConfig.SessionTimeoutTicks.get();
        int keep = DSConfig.KeepFinishedSessions.get();
        boolean changed = tickAll(outgoing.values(), currentGameTime, timeout, keep);
        changed |= tickAll(incoming.values(), currentGameTime, timeout, keep);
        changed |= tickAll(outgoingByType.values(), currentGameTime, timeout, keep);
        changed |= tickAll(incomingByType.values(), currentGameTime, timeout, keep);
        return global.tick(currentGameTime, timeout, keep) || changed;
    }

    public void reset() {
        outgoing.clear();
        incoming.clear();
        outgoingByType.clear();
        incomingByType.clear();
        global = new StatsEntry();
        nameCache.clear();
        instanceDirectory.clear();
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

    public Map<UUID, String> cachedNames() {
        return Map.copyOf(nameCache);
    }

    private void recoverName(EntityRef ref, String name) {
        if(!name.isBlank() && !ref.isEnvironment() && !ref.isTypeReference()) {
            nameCache.put(ref.id(), name);
        }
    }

    private static boolean clearedBefore(UUID ownerId, long sequence, Map<UUID, Long> resetSequences) {
        Long resetSequence = resetSequences.get(ownerId);
        return resetSequence != null && sequence < resetSequence;
    }

    public void touchInstance(Entity entity, long nowMillis) {
        instanceDirectory.touch(entity, nowMillis);
    }

    public void pruneInstanceDirectory(long nowMillis) {
        instanceDirectory.prune(nowMillis);
        Set<UUID> knownInstances = instanceDirectory.entries().stream()
                .map(metadata -> metadata.ref().id())
                .collect(java.util.stream.Collectors.toSet());
        nameCache.keySet().retainAll(knownInstances);
    }

    public InstanceDirectory instanceDirectory() {
        return instanceDirectory;
    }

    /** 传 null 表示 Overlay 不限制目标类型 */
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
        resetFor(owner.id());
    }

    public void resetFor(UUID ownerId) {
        outgoing.keySet().removeIf(ref -> ref.id().equals(ownerId));
        incoming.keySet().removeIf(ref -> ref.id().equals(ownerId));
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
                                          Optional<StatsEntry> global,
                                          InstanceDirectory instanceDirectory) {
        DamageTracker tracker = new DamageTracker();
        outgoing.forEach(entry -> tracker.outgoing.put(entry.owner(), entry.stats()));
        incoming.forEach(entry -> tracker.incoming.put(entry.owner(), entry.stats()));
        tracker.outgoingByType.putAll(outgoingByType);
        tracker.incomingByType.putAll(incomingByType);
        tracker.nameCache.putAll(names);
        tracker.instanceDirectory.restore(instanceDirectory.entries());
        if(global.isPresent()) {
            tracker.global = global.get();
        } else {
            outgoingByType.values().forEach(tracker.global::absorbLifetime);
        }
        return tracker;
    }

    private static boolean tickAll(Collection<StatsEntry> entries, long gameTime, int timeout, int keep) {
        boolean changed = false;
        for (StatsEntry entry : entries) {
            if(entry.tick(gameTime, timeout, keep)) changed = true;
        }
        return changed;
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
