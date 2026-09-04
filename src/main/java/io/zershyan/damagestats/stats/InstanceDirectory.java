package io.zershyan.damagestats.stats;

import com.mojang.serialization.Codec;
import io.zershyan.damagestats.config.DSConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;

/** 受限的实例候选目录。历史事件独立保存，淘汰这里的候选不会丢失伤害记录。 */
public class InstanceDirectory {
    public static final Codec<InstanceDirectory> CODEC = InstanceMetadata.CODEC.listOf()
            .xmap(InstanceDirectory::from, InstanceDirectory::entries);

    private final Map<UUID, InstanceMetadata> entries = new HashMap<>();

    public void touch(LivingEntity entity, long nowMillis) {
        InstanceMetadata metadata = InstanceMetadata.from(entity, nowMillis);
        entries.put(metadata.ref().id(), metadata);
        prune(nowMillis);
    }

    public void prune(long nowMillis) {
        long retentionMillis = DSConfig.InstanceDirectoryRetentionHours.get() * 60L * 60L * 1000L;
        entries.values().removeIf(metadata -> nowMillis - metadata.lastInteractionMillis() > retentionMillis);
        int limit = DSConfig.InstanceDirectoryLimit.get();
        while(entries.size() > limit) {
            InstanceMetadata oldest = entries.values().stream()
                    .min(Comparator.comparingLong(InstanceMetadata::lastInteractionMillis))
                    .orElse(null);
            if(oldest == null) return;
            entries.remove(oldest.ref().id());
        }
    }

    public List<InstanceMetadata> entries() {
        return entries.values().stream()
                .sorted(Comparator.comparingLong(InstanceMetadata::lastInteractionMillis).reversed())
                .toList();
    }

    public boolean contains(EntityRef ref) {
        return entries.containsKey(ref.id());
    }

    public boolean containsType(ResourceLocation typeId) {
        return entries.values().stream().anyMatch(metadata -> typeId.equals(metadata.ref().typeId()));
    }

    public void restore(Collection<InstanceMetadata> restored) {
        entries.clear();
        restored.forEach(metadata -> entries.put(metadata.ref().id(), metadata));
    }

    public void clear() {
        entries.clear();
    }

    private static InstanceDirectory from(List<InstanceMetadata> restored) {
        InstanceDirectory directory = new InstanceDirectory();
        directory.restore(restored);
        return directory;
    }
}
