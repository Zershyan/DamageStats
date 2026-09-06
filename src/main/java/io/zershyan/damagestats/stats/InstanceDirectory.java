package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import io.zershyan.damagestats.config.DSConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.*;

/** 受限的实例候选目录。历史事件独立保存，淘汰这里的候选不会丢失伤害记录。 */
public class InstanceDirectory {
    public static final Codec<InstanceDirectory> CODEC = InstanceMetadata.CODEC.listOf()
            .xmap(InstanceDirectory::from, InstanceDirectory::entries);

    private final Map<UUID, InstanceMetadata> entries = new HashMap<>();
    private long revision;
    private long orderedRevision = -1;
    private List<InstanceMetadata> orderedEntries = List.of();

    public void touch(Entity entity, long nowMillis) {
        InstanceMetadata metadata = InstanceMetadata.from(entity, nowMillis);
        entries.put(metadata.ref().id(), metadata);
        invalidate();
        prune(nowMillis);
    }

    public void prune(long nowMillis) {
        long retentionMillis = DSConfig.InstanceDirectoryRetentionHours.get() * 60L * 60L * 1000L;
        boolean changed = entries.values().removeIf(metadata ->
                nowMillis - metadata.lastInteractionMillis() > retentionMillis);
        int limit = DSConfig.InstanceDirectoryLimit.get();
        while(entries.size() > limit) {
            InstanceMetadata oldest = entries.values().stream()
                    .min(Comparator.comparingLong(InstanceMetadata::lastInteractionMillis))
                    .orElse(null);
            if(oldest == null) return;
            entries.remove(oldest.ref().id());
            changed = true;
        }
        if(changed) invalidate();
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

    public boolean containsType(ResourceLocation typeId) {
        return entries.values().stream().anyMatch(metadata -> typeId.equals(metadata.ref().typeId()));
    }

    public void restore(Collection<InstanceMetadata> restored) {
        entries.clear();
        restored.forEach(metadata -> entries.put(metadata.ref().id(), metadata));
        invalidate();
    }

    public void clear() {
        entries.clear();
        invalidate();
    }

    private void invalidate() {
        revision++;
        orderedRevision = -1;
    }

    private static InstanceDirectory from(List<InstanceMetadata> restored) {
        InstanceDirectory directory = new InstanceDirectory();
        directory.restore(restored);
        return directory;
    }
}
