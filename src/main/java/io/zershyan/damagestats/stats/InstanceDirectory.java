package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import io.zershyan.damagestats.config.DSConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** 受限的实例候选目录。历史事件独立保存，淘汰这里的候选不会丢失伤害记录。 */
public class InstanceDirectory {
    public static final Codec<InstanceDirectory> CODEC = InstanceMetadata.CODEC.listOf()
            .xmap(InstanceDirectory::from, InstanceDirectory::entries);

    private final Map<UUID, InstanceMetadata> entries = new HashMap<>();
    private long nextInstanceId = 1L;
    private long revision;
    private long orderedRevision = -1;
    private List<InstanceMetadata> orderedEntries = List.of();

    public void touch(Entity entity, long nowMillis) {
        InstanceMetadata metadata = withId(InstanceMetadata.from(entity, nowMillis), entries.get(entity.getUUID()));
        entries.put(metadata.ref().id(), metadata);
        invalidate();
        prune(nowMillis);
    }

    void touchFromRecord(EntityRef ref, DamageRecord record, String recoveredName) {
        if(ref.isEnvironment() || ref.typeId() == null) return;
        InstanceMetadata previous = entries.get(ref.id());
        if(previous != null && previous.lastInteractionMillis() > record.occurredAtMillis()) return;
        String displayName = recoveredName.isBlank() && previous != null
                ? previous.displayName() : recoveredName;
        long occurredAt = record.occurredAtMillis() > 0
                ? record.occurredAtMillis() : System.currentTimeMillis();
        entries.put(ref.id(), withId(new InstanceMetadata(0L, ref, displayName, record.dimensionId(),
                record.blockX(), record.blockY(), record.blockZ(), occurredAt, record.gameTime()), previous));
        invalidate();
    }

    public void prune(long nowMillis) {
        long retentionMillis = DSConfig.InstanceDirectoryRetentionHours.get() * 60L * 60L * 1000L;
        // 先按保留期淘汰，但保护玩家条目
        boolean changed = entries.values().removeIf(metadata ->
                !isPlayerType(metadata.ref().typeId())
                && nowMillis - metadata.lastInteractionMillis() > retentionMillis);

        // 按上限淘汰时，保护玩家和高价值实体（如 Boss）
        int limit = DSConfig.InstanceDirectoryLimit.get();
        while(entries.size() > limit) {
            InstanceMetadata oldest = entries.values().stream()
                    .filter(metadata -> !isProtected(metadata))
                    .min(Comparator.comparingLong(InstanceMetadata::lastInteractionMillis))
                    .orElse(null);
            if(oldest == null) return; // 剩余全是受保护的实例
            entries.remove(oldest.ref().id());
            changed = true;
        }
        if(changed) invalidate();
    }

    /** 玩家条目永不淘汰 */
    private static boolean isPlayerType(ResourceLocation typeId) {
        return typeId != null && "minecraft:player".equals(typeId.toString());
    }

    /** 受保护的实例：玩家、驯服生物（名称非空的非玩家实体视为驯服或命名） */
    private static boolean isProtected(InstanceMetadata metadata) {
        if(isPlayerType(metadata.ref().typeId())) return true;
        // 有自定义名的实体视为玩家关心的重要实体（驯服生物、命名怪物等）
        return metadata.displayName() != null && !metadata.displayName().isBlank();
    }

    public List<InstanceMetadata> entries() {
        if(orderedRevision != revision) {
            orderedEntries = entries.values().stream()
                    .sorted(Comparator.comparingLong(InstanceMetadata::lastInteractionMillis).reversed())
                    .toList();
            orderedRevision = revision;
        }
        return orderedEntries;
    }

    public long revision() {
        return revision;
    }

    public boolean contains(EntityRef ref) {
        InstanceMetadata metadata = entries.get(ref.id());
        return metadata != null && metadata.ref().equals(ref);
    }

    public InstanceMetadata metadata(EntityRef ref) {
        InstanceMetadata metadata = entries.get(ref.id());
        return metadata != null && metadata.ref().equals(ref) ? metadata : null;
    }

    public boolean containsType(ResourceLocation typeId) {
        return entries.values().stream().anyMatch(metadata -> typeId.equals(metadata.ref().typeId()));
    }

    public void restore(Collection<InstanceMetadata> restored) {
        entries.clear();
        nextInstanceId = 1L;
        Set<Long> usedIds = new HashSet<>();
        for(InstanceMetadata metadata : restored) {
            long instanceId = metadata.instanceId();
            if(instanceId <= 0 || !usedIds.add(instanceId)) instanceId = allocateId(usedIds);
            nextInstanceId = Math.max(nextInstanceId, instanceId + 1);
            entries.put(metadata.ref().id(), withId(metadata, instanceId));
        }
        invalidate();
    }

    public void clear() {
        entries.clear();
        nextInstanceId = 1L;
        invalidate();
    }

    private void invalidate() {
        revision++;
        orderedRevision = -1;
    }

    private InstanceMetadata withId(InstanceMetadata metadata, @Nullable InstanceMetadata previous) {
        long instanceId = previous != null && previous.instanceId() > 0
                ? previous.instanceId() : allocateId(null);
        return withId(metadata, instanceId);
    }

    private InstanceMetadata withId(InstanceMetadata metadata, long instanceId) {
        nextInstanceId = Math.max(nextInstanceId, instanceId + 1);
        return new InstanceMetadata(instanceId, metadata.ref(), metadata.displayName(), metadata.dimensionId(),
                metadata.blockX(), metadata.blockY(), metadata.blockZ(), metadata.lastInteractionMillis(),
                metadata.lastInteractionGameTime());
    }

    private long allocateId(@Nullable Set<Long> usedIds) {
        long candidate = Math.max(1L, nextInstanceId);
        if(usedIds != null) {
            while(usedIds.contains(candidate)) candidate++;
            usedIds.add(candidate);
        }
        nextInstanceId = candidate + 1;
        return candidate;
    }

    private static InstanceDirectory from(List<InstanceMetadata> restored) {
        InstanceDirectory directory = new InstanceDirectory();
        directory.restore(restored);
        return directory;
    }
}
