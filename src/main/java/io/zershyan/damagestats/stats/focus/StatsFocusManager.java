package io.zershyan.damagestats.stats.focus;

import io.zershyan.damagestats.stats.DamageRecord;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import io.zershyan.damagestats.stats.view.FocusSummary;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 管理临时焦点及其订阅索引。焦点不进存档，实体实例下钻授权也只在当前 GUI 会话有效。
 */
public final class StatsFocusManager {
    private static final class PlayerFocus {
        private StatsFocus focus;
        private final Set<EntityRef> delegatedSources = new HashSet<>();
        private @Nullable FocusSummaryCache summaryCache;

        private PlayerFocus(StatsFocus focus) {
            this.focus = focus;
        }
    }

    private final Map<UUID, PlayerFocus> focuses = new HashMap<>();
    private final Map<UUID, Set<UUID>> sourceInstances = new HashMap<>();
    private final Map<ResourceLocation, Set<UUID>> sourceTypes = new HashMap<>();
    private final Map<UUID, Set<UUID>> directSourceInstances = new HashMap<>();
    private final Map<ResourceLocation, Set<UUID>> directSourceTypes = new HashMap<>();
    private final Map<UUID, Set<UUID>> targetInstances = new HashMap<>();
    private final Map<ResourceLocation, Set<UUID>> targetTypes = new HashMap<>();
    private final Set<UUID> unrestrictedSources = new HashSet<>();
    private final Set<UUID> unrestrictedDirectSources = new HashSet<>();
    private final Set<UUID> unrestrictedTargets = new HashSet<>();

    public StatsFocus focusFor(ServerPlayer player) {
        return stateFor(player).focus;
    }

    public FocusChangeResult setFocus(ServerPlayer player, Optional<EntitySelector> source,
                                       Optional<EntitySelector> target, boolean sourceIsDirectSource,
                                       DamageTracker tracker) {
        PlayerFocus state = stateFor(player);
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        boolean targetChanged = !state.focus.target().equals(target);
        if(targetChanged) {
            state.delegatedSources.clear();
            if(!player.hasPermissions(2) && !state.focus.source().filter(self::equals).isPresent()) {
                source = Optional.of(self);
                sourceIsDirectSource = false;
            }
        }

        FocusChangeResult sourceResult = validateSource(player, source, target, sourceIsDirectSource, state, self, tracker);
        if(sourceResult != FocusChangeResult.ACCEPTED) return sourceResult;
        if(target.isPresent() && !isKnownTarget(target.get(), tracker)) return FocusChangeResult.UNKNOWN_TARGET;

        state.focus = state.focus.next(source, target, sourceIsDirectSource);
        state.summaryCache = FocusSummaryCache.load(tracker, player, state.focus);
        reindex(player.getUUID(), state.focus);
        return FocusChangeResult.ACCEPTED;
    }

    /** 首次订阅和焦点切换允许建立一次缓存；后续同步仅读取该缓存。 */
    public FocusSummary summaryFor(DamageTracker tracker, ServerPlayer player) {
        PlayerFocus state = stateFor(player);
        if(state.summaryCache == null) state.summaryCache = FocusSummaryCache.load(tracker, player, state.focus);
        return state.summaryCache.summary(tracker, player.level().getGameTime());
    }

    public boolean canBrowse(ServerPlayer player, StatsFilter filter, DamageTracker tracker) {
        if(player.hasPermissions(2)) return true;
        return canBrowseSource(player, filter.source(), filter.target(), filter.sourceIsDirectSource(), tracker);
    }

    public boolean canBrowseSource(ServerPlayer player, Optional<EntitySelector> source,
                                   Optional<EntitySelector> target, boolean sourceIsDirectSource,
                                   DamageTracker tracker) {
        if(source.isEmpty()) return false;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        PlayerFocus state = stateFor(player);
        if(sourceIsDirectSource) return source.get() instanceof EntitySelector.Instance(EntityRef ref)
                && (state.delegatedSources.contains(ref) || isDirectSourceOfPlayer(player, target, ref, tracker));
        if(source.filter(self::equals).isPresent()) return true;
        return source.get() instanceof EntitySelector.Instance(EntityRef ref)
                && state.delegatedSources.contains(ref);
    }

    /** 普通玩家关闭 GUI 后立即失去实体来源下钻权限，并恢复为自身来源。 */
    public boolean closeGui(ServerPlayer player) {
        PlayerFocus state = focuses.get(player.getUUID());
        if(state == null) return false;
        state.delegatedSources.clear();
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        if(state.focus.source().filter(self::equals).isPresent()) return false;
        state.focus = state.focus.next(Optional.of(self), state.focus.target(), false);
        state.summaryCache = null;
        reindex(player.getUUID(), state.focus);
        return true;
    }

    /** 仅允许从当前玩家自身输出链路中出现过的 LivingEntity 直接来源下钻。 */
    public FocusChangeResult selectDirectSource(ServerPlayer player, EntityRef source, DamageTracker tracker) {
        if(!tracker.instanceDirectory().contains(source)) return FocusChangeResult.UNKNOWN_SOURCE;
        PlayerFocus state = stateFor(player);
        if(player.hasPermissions(2)) {
            state.focus = state.focus.next(Optional.of(new EntitySelector.Instance(source)), state.focus.target(), true);
            state.summaryCache = FocusSummaryCache.load(tracker, player, state.focus);
            reindex(player.getUUID(), state.focus);
            return FocusChangeResult.ACCEPTED;
        }
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        if(!state.focus.source().filter(self::equals).isPresent()) return FocusChangeResult.SOURCE_NOT_ALLOWED;
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return FocusChangeResult.UNKNOWN_SOURCE;
        StatsFilter proof = new StatsFilter(Optional.of(self), state.focus.target(),
                Optional.of(new EntitySelector.Instance(source)), Optional.empty());
        if(journal.matching(proof).isEmpty()) return FocusChangeResult.SOURCE_NOT_ALLOWED;
        state.delegatedSources.add(source);
        state.focus = state.focus.next(Optional.of(new EntitySelector.Instance(source)), state.focus.target(), true);
        state.summaryCache = FocusSummaryCache.load(tracker, player, state.focus);
        reindex(player.getUUID(), state.focus);
        return FocusChangeResult.ACCEPTED;
    }

    /** 订阅索引先缩小候选集，再以完整焦点筛选确认并增量更新命中的缓存。 */
    public Set<UUID> matchingSubscribers(DamageRecord record) {
        Set<UUID> sources = selectorSubscribers(record.source(), sourceInstances, sourceTypes, unrestrictedSources);
        sources.addAll(selectorSubscribers(record.directSource(), directSourceInstances, directSourceTypes,
                unrestrictedDirectSources));
        Set<UUID> targets = selectorSubscribers(record.target(), targetInstances, targetTypes, unrestrictedTargets);
        sources.retainAll(targets);
        sources.removeIf(playerId -> {
            PlayerFocus state = focuses.get(playerId);
            return state == null || !state.focus.matches(record);
        });
        sources.forEach(playerId -> {
            FocusSummaryCache cache = focuses.get(playerId).summaryCache;
            if(cache != null) cache.accept(record);
        });
        return sources;
    }

    /** 仅在会话检查时更新确实因超时发生变化的焦点。 */
    public Set<UUID> expireSessions(long gameTime) {
        Set<UUID> expired = new HashSet<>();
        focuses.forEach((playerId, state) -> {
            if(state.summaryCache != null && state.summaryCache.expire(gameTime)) expired.add(playerId);
        });
        return expired;
    }

    public void invalidateSummaryCaches() {
        focuses.values().forEach(state -> state.summaryCache = null);
    }

    public void remove(UUID playerId) {
        focuses.remove(playerId);
        removeFromIndexes(playerId);
    }

    public void clear() {
        focuses.clear();
        sourceInstances.clear();
        sourceTypes.clear();
        directSourceInstances.clear();
        directSourceTypes.clear();
        targetInstances.clear();
        targetTypes.clear();
        unrestrictedSources.clear();
        unrestrictedDirectSources.clear();
        unrestrictedTargets.clear();
    }

    private PlayerFocus stateFor(ServerPlayer player) {
        return focuses.computeIfAbsent(player.getUUID(), ignored -> {
            PlayerFocus state = new PlayerFocus(StatsFocus.forPlayer(player));
            reindex(player.getUUID(), state.focus);
            return state;
        });
    }

    private FocusChangeResult validateSource(ServerPlayer player, Optional<EntitySelector> source,
                                             Optional<EntitySelector> target, boolean sourceIsDirectSource,
                                             PlayerFocus state, EntitySelector self, DamageTracker tracker) {
        if(player.hasPermissions(2)) {
            if(source.isEmpty() || isKnown(source.get(), tracker)) return FocusChangeResult.ACCEPTED;
            return FocusChangeResult.UNKNOWN_SOURCE;
        }
        if(sourceIsDirectSource && source.orElse(null) instanceof EntitySelector.Instance(EntityRef ref)
                && isDirectSourceOfPlayer(player, target, ref, tracker)) {
            state.delegatedSources.add(ref);
            return FocusChangeResult.ACCEPTED;
        }
        if(source.filter(self::equals).isPresent()) return FocusChangeResult.ACCEPTED;
        return FocusChangeResult.SOURCE_NOT_ALLOWED;
    }

    private static boolean isDirectSourceOfPlayer(ServerPlayer player, Optional<EntitySelector> target,
                                                  EntityRef source, DamageTracker tracker) {
        if(!tracker.instanceDirectory().contains(source)) return false;
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return false;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        StatsFilter proof = new StatsFilter(Optional.of(self), target,
                Optional.of(new EntitySelector.Instance(source)), Optional.empty());
        return !journal.matching(proof).isEmpty();
    }

    private static boolean isKnown(EntitySelector selector, DamageTracker tracker) {
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) -> tracker.instanceDirectory().contains(ref);
            case EntitySelector.Type(ResourceLocation typeId) -> tracker.instanceDirectory().containsType(typeId);
        };
    }

    private static boolean isKnownTarget(EntitySelector selector, DamageTracker tracker) {
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) -> tracker.instanceDirectory().contains(ref);
            case EntitySelector.Type(ResourceLocation typeId) -> isLivingType(typeId);
        };
    }

    private static boolean isLivingType(ResourceLocation typeId) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        return type != null && LivingEntity.class.isAssignableFrom(type.getBaseClass());
    }

    private void reindex(UUID playerId, StatsFocus focus) {
        removeFromIndexes(playerId);
        if(focus.sourceIsDirectSource()) {
            index(focus.source(), playerId, directSourceInstances, directSourceTypes, unrestrictedDirectSources);
        } else {
            index(focus.source(), playerId, sourceInstances, sourceTypes, unrestrictedSources);
        }
        index(focus.target(), playerId, targetInstances, targetTypes, unrestrictedTargets);
    }

    private void removeFromIndexes(UUID playerId) {
        remove(sourceInstances, playerId);
        remove(sourceTypes, playerId);
        remove(directSourceInstances, playerId);
        remove(directSourceTypes, playerId);
        remove(targetInstances, playerId);
        remove(targetTypes, playerId);
        unrestrictedSources.remove(playerId);
        unrestrictedDirectSources.remove(playerId);
        unrestrictedTargets.remove(playerId);
    }

    private static <K> void remove(Map<K, Set<UUID>> index, UUID playerId) {
        index.values().forEach(subscribers -> subscribers.remove(playerId));
        index.values().removeIf(Set::isEmpty);
    }

    private static void index(Optional<EntitySelector> selector, UUID playerId,
                              Map<UUID, Set<UUID>> instances,
                              Map<ResourceLocation, Set<UUID>> types,
                              Set<UUID> unrestricted) {
        if(selector.isEmpty()) {
            unrestricted.add(playerId);
            return;
        }
        switch (selector.get()) {
            case EntitySelector.Instance(EntityRef ref) ->
                    instances.computeIfAbsent(ref.id(), ignored -> new HashSet<>()).add(playerId);
            case EntitySelector.Type(ResourceLocation typeId) ->
                    types.computeIfAbsent(typeId, ignored -> new HashSet<>()).add(playerId);
        }
    }

    private static Set<UUID> selectorSubscribers(EntityRef ref, Map<UUID, Set<UUID>> instances,
                                                  Map<ResourceLocation, Set<UUID>> types,
                                                  Set<UUID> unrestricted) {
        Set<UUID> result = new HashSet<>(unrestricted);
        addAll(result, instances.get(ref.id()));
        addAll(result, types.get(ref.typeIdOrEnvironment()));
        return result;
    }

    private static void addAll(Set<UUID> target, @Nullable Set<UUID> additions) {
        if(additions != null) target.addAll(additions);
    }
}
