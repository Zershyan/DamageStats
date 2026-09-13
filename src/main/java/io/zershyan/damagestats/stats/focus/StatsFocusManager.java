package io.zershyan.damagestats.stats.focus;

import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.stats.DamageRecord;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import io.zershyan.damagestats.stats.view.FocusSummary;
import io.zershyan.damagestats.util.EntityTypeHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 管理临时焦点及其订阅索引。焦点不进存档，实体实例下钻授权也只在当前 GUI 会话有效。
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class StatsFocusManager {
    private static final ResourceLocation PLAYER_TYPE = ResourceLocation.withDefaultNamespace("player");

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
        if(source.filter(StatsFocusManager::isPlayerSelector).isPresent()) sourceIsDirectSource = false;
        boolean targetChanged = !state.focus.target().equals(target);
        if(targetChanged) {
            // 目标改变时，清空下钻授权并验证下钻来源是否仍合法
            state.delegatedSources.clear();
            if(!hasFullAccess(player)) {
                // 若当前来源是下钻的直接来源，检查其是否在新目标下仍合法
                if(state.focus.sourceIsDirectSource()) {
                    EntitySelector currentSource = state.focus.source().orElse(null);
                    if(currentSource instanceof EntitySelector.Instance(EntityRef ref)) {
                        // 验证该下钻来源在新目标下是否仍出现在玩家的伤害路径中
                        if(!isDirectSourceOfPlayer(player, target, ref, tracker)) {
                            // 不合法则强制恢复为玩家自身
                            source = Optional.of(self);
                            sourceIsDirectSource = false;
                        }
                    } else {
                        source = Optional.of(self);
                        sourceIsDirectSource = false;
                    }
                } else if(state.focus.source().filter(self::equals).isEmpty()) {
                    source = Optional.of(self);
                    sourceIsDirectSource = false;
                }
            }
        }

        FocusChangeResult sourceResult = validateSource(player, source, target, sourceIsDirectSource, state, self, tracker);
        if(sourceResult != FocusChangeResult.ACCEPTED) return sourceResult;
        if(target.filter(selector -> !isKnownTarget(selector, tracker)).isPresent()) {
            return FocusChangeResult.UNKNOWN_TARGET;
        }

        state.focus = state.focus.next(source, target, sourceIsDirectSource);
        state.summaryCache = FocusSummaryCache.load(tracker, player, state.focus);
        reindex(player.getUUID(), state.focus);
        return FocusChangeResult.ACCEPTED;
    }

    /** GUI 主动刷新时从权威事件日志重建摘要，避免增量同步前后出现空数据或漂移。 */
    public void refreshSummary(DamageTracker tracker, ServerPlayer player) {
        PlayerFocus state = stateFor(player);
        state.summaryCache = FocusSummaryCache.load(tracker, player, state.focus);
    }

    /** 首次订阅和焦点切换允许建立一次缓存；后续同步仅读取该缓存。 */
    public FocusSummary summaryFor(DamageTracker tracker, ServerPlayer player) {
        PlayerFocus state = stateFor(player);
        FocusSummaryCache cache = state.summaryCache;
        if(cache == null) {
            cache = FocusSummaryCache.load(tracker, player, state.focus);
            state.summaryCache = cache;
        }
        return cache.summary(tracker, player.level().getGameTime());
    }

    public boolean canBrowse(ServerPlayer player, StatsFilter filter, DamageTracker tracker) {
        if(!isValidFilter(player, filter, tracker)) return false;
        if(hasFullAccess(player)) return true;
        boolean sourceAllowed = canBrowseSource(player, filter.source(), filter.target(),
                filter.sourceIsDirectSource(), tracker);
        if(!sourceAllowed) return false;
        if(DSConfig.PublicStats.get()) return true;
        return isPrivateSource(player, filter.source(), filter.sourceIsDirectSource(), tracker);
    }

    public boolean canBrowseSource(ServerPlayer player, Optional<EntitySelector> source,
                                   Optional<EntitySelector> target, boolean sourceIsDirectSource,
                                   DamageTracker tracker) {
        if(source.isEmpty()) return false;
        if(source.filter(StatsFocusManager::isPlayerSelector).isPresent()) sourceIsDirectSource = false;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        PlayerFocus state = stateFor(player);
        EntitySelector selected = source.orElse(null);
        if(sourceIsDirectSource) return selected instanceof EntitySelector.Instance(EntityRef ref)
                && isLivingRef(ref) && tracker.instanceDirectory().contains(ref)
                && (state.delegatedSources.contains(ref) || isDirectSourceOfPlayer(player, target, ref, tracker));
        return self.equals(selected) && isValidSourceSelector(player, selected, tracker);
    }

    /** 普通玩家关闭 GUI 后立即失去实体来源下钻权限，并恢复为自身来源。 */
    public boolean closeGui(ServerPlayer player) {
        PlayerFocus state = focuses.get(player.getUUID());
        if(state == null) return false;
        state.delegatedSources.clear();
        if(hasFullAccess(player)) return false;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        if(!state.focus.sourceIsDirectSource() && state.focus.source().filter(self::equals).isPresent()) return false;
        state.focus = state.focus.next(Optional.of(self), state.focus.target(), false);
        state.summaryCache = null;
        reindex(player.getUUID(), state.focus);
        return true;
    }

    /** 仅允许从当前玩家自身输出链路中出现过的 LivingEntity 直接来源下钻。 */
    public FocusChangeResult selectDirectSource(ServerPlayer player, EntityRef source, DamageTracker tracker) {
        if(!isLivingRef(source) || !tracker.instanceDirectory().contains(source)) return FocusChangeResult.UNKNOWN_SOURCE;
        PlayerFocus state = stateFor(player);
        boolean sourceIsDirectSource = !PLAYER_TYPE.equals(source.typeId());
        if(hasFullAccess(player)) {
            state.focus = state.focus.next(Optional.of(new EntitySelector.Instance(source)), state.focus.target(),
                    sourceIsDirectSource);
            state.summaryCache = FocusSummaryCache.load(tracker, player, state.focus);
            reindex(player.getUUID(), state.focus);
            return FocusChangeResult.ACCEPTED;
        }
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        if(state.focus.source().filter(self::equals).isEmpty()) return FocusChangeResult.SOURCE_NOT_ALLOWED;
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return FocusChangeResult.UNKNOWN_SOURCE;
        StatsFilter proof = new StatsFilter(Optional.of(self), state.focus.target(),
                Optional.of(new EntitySelector.Instance(source)), Optional.empty());
        if(journal.matching(proof).isEmpty()) return FocusChangeResult.SOURCE_NOT_ALLOWED;
        state.delegatedSources.add(source);
        state.focus = state.focus.next(Optional.of(new EntitySelector.Instance(source)), state.focus.target(),
                sourceIsDirectSource);
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
            PlayerFocus state = focuses.get(playerId);
            if(state == null) return;
            FocusSummaryCache cache = state.summaryCache;
            if(cache != null) cache.accept(record);
        });
        return sources;
    }

    /** 仅在会话检查时更新确实因超时发生变化的焦点。 */
    public Set<UUID> expireSessions(long gameTime) {
        Set<UUID> expired = new HashSet<>();
        focuses.forEach((playerId, state) -> {
            FocusSummaryCache cache = state.summaryCache;
            if(cache != null && cache.expire(gameTime)) expired.add(playerId);
        });
        return expired;
    }

    public void invalidateSummaryCaches(Collection<ServerPlayer> onlinePlayers) {
        Map<UUID, ServerPlayer> online = new HashMap<>();
        onlinePlayers.forEach(player -> online.put(player.getUUID(), player));
        focuses.forEach((playerId, state) -> {
            state.summaryCache = null;
            state.delegatedSources.clear();
            ServerPlayer player = online.get(playerId);
            if(player != null && !hasFullAccess(player)) restoreOrdinarySource(state, playerId);
            reindex(playerId, state.focus);
        });
    }

    public void invalidateSummaryCache(ServerPlayer player) {
        UUID playerId = player.getUUID();
        PlayerFocus state = focuses.get(playerId);
        if(state == null) return;
        state.summaryCache = null;
        state.delegatedSources.clear();
        if(!hasFullAccess(player)) restoreOrdinarySource(state, playerId);
        reindex(playerId, state.focus);
    }

    public void resetSummaryCache(ServerPlayer player, DamageTracker tracker) {
        UUID playerId = player.getUUID();
        PlayerFocus state = focuses.get(playerId);
        if(state == null) return;
        state.delegatedSources.clear();
        if(!hasFullAccess(player)) restoreOrdinarySource(state, playerId);
        state.summaryCache = FocusSummaryCache.load(tracker, player, state.focus);
        reindex(playerId, state.focus);
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
        if(sourceIsDirectSource) {
            if(!(source.orElse(null) instanceof EntitySelector.Instance(EntityRef ref))
                    || !isLivingRef(ref) || !tracker.instanceDirectory().contains(ref)) {
                return FocusChangeResult.UNKNOWN_SOURCE;
            }
            if(hasFullAccess(player)) return FocusChangeResult.ACCEPTED;
            if(isDirectSourceOfPlayer(player, target, ref, tracker)) {
                state.delegatedSources.add(ref);
                return FocusChangeResult.ACCEPTED;
            }
            return FocusChangeResult.SOURCE_NOT_ALLOWED;
        }
        if(hasFullAccess(player)) {
            return source.map(selector -> isValidSourceSelector(player, selector, tracker)).orElse(true)
                    ? FocusChangeResult.ACCEPTED : FocusChangeResult.UNKNOWN_SOURCE;
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

    public static boolean hasFullAccess(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return player.hasPermissions(2)
                || server != null && server.isSingleplayerOwner(player.getGameProfile());
    }

    private static boolean isValidSourceSelector(ServerPlayer player, EntitySelector selector,
                                                 DamageTracker tracker) {
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) -> isLivingRef(ref)
                    && (ref.equals(EntityRef.of(player)) || tracker.instanceDirectory().contains(ref));
            case EntitySelector.Type(ResourceLocation typeId) -> isRecordedLivingSourceType(player, typeId);
        };
    }

    private static boolean isKnownTarget(EntitySelector selector, DamageTracker tracker) {
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) -> isLivingRef(ref) && tracker.instanceDirectory().contains(ref);
            case EntitySelector.Type(ResourceLocation typeId) -> isLivingType(typeId);
        };
    }

    private static boolean isLivingRef(EntityRef ref) {
        return ref.typeId() != null && isLivingType(ref.typeId());
    }

    private static boolean isValidFilter(ServerPlayer player, StatsFilter filter, DamageTracker tracker) {
        if(filter.sourceIsDirectSource()) {
            if(!(filter.source().orElse(null) instanceof EntitySelector.Instance(EntityRef ref))
                    || !isLivingRef(ref) || !tracker.instanceDirectory().contains(ref)) return false;
        } else if(filter.source().map(selector -> !isValidSourceSelector(player, selector, tracker)).orElse(false)) {
            return false;
        }
        if(filter.target().map(selector -> !isKnownTarget(selector, tracker)).orElse(false)) return false;
        if(filter.directSource().map(selector -> !isValidDirectSourceSelector(selector, tracker)).orElse(false)) {
            return false;
        }
        return filter.damageType().map(StatsFocusManager::isValidDamageTypeSelector).orElse(true);
    }

    private static boolean isValidDirectSourceSelector(EntitySelector selector, DamageTracker tracker) {
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) -> ref.typeId() != null
                    && tracker.instanceDirectory().contains(ref);
            case EntitySelector.Type(ResourceLocation typeId) -> {
                DamageEventJournal journal = ServerStats.journal();
                yield journal != null && journal.recordedDirectSourceTypes().contains(typeId);
            }
        };
    }

    private static boolean isValidDamageTypeSelector(
            io.zershyan.damagestats.stats.filter.DamageTypeSelector selector) {
        return switch (selector) {
            case io.zershyan.damagestats.stats.filter.DamageTypeSelector.Category(String name) ->
                    DamageTypeCategories.hasCategory(name);
            case io.zershyan.damagestats.stats.filter.DamageTypeSelector.Exact(ResourceLocation id) -> {
                DamageEventJournal journal = ServerStats.journal();
                yield journal != null && journal.recordedDamageTypes().contains(id);
            }
        };
    }

    private boolean isPrivateSource(ServerPlayer player, Optional<EntitySelector> source,
                                    boolean sourceIsDirectSource, DamageTracker tracker) {
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        if(source.filter(self::equals).isPresent()) return true;
        if(!(source.orElse(null) instanceof EntitySelector.Instance(EntityRef ref))) return false;
        if(!isLivingRef(ref)) return false;
        PlayerFocus state = stateFor(player);
        return sourceIsDirectSource && (state.delegatedSources.contains(ref)
                || isDirectSourceOfPlayer(player, Optional.empty(), ref, tracker));
    }

    private static boolean isLivingType(ResourceLocation typeId) {
        return EntityTypeHelper.isLivingType(typeId);
    }

    private static boolean isPlayerSelector(EntitySelector selector) {
        return switch (selector) {
            case EntitySelector.Instance(EntityRef ref) -> PLAYER_TYPE.equals(ref.typeId());
            case EntitySelector.Type(ResourceLocation typeId) -> PLAYER_TYPE.equals(typeId);
        };
    }

    private static boolean isRecordedLivingSourceType(ServerPlayer player, ResourceLocation typeId) {
        if(!isLivingType(typeId)) return false;
        if(hasFullAccess(player)) return true;
        DamageEventJournal journal = ServerStats.journal();
        return journal != null && journal.recordedSourceTypesIncludingDirect().contains(typeId);
    }

    private static void restoreOrdinarySource(PlayerFocus state, UUID playerId) {
        if(!state.focus.sourceIsDirectSource()) return;
        EntityRef self = new EntityRef(playerId, ResourceLocation.withDefaultNamespace("player"));
        state.focus = state.focus.next(Optional.of(new EntitySelector.Instance(self)), state.focus.target(), false);
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
        selector.ifPresentOrElse(value -> {
            switch (value) {
                case EntitySelector.Instance(EntityRef ref) ->
                        instances.computeIfAbsent(ref.id(), ignored -> new HashSet<>()).add(playerId);
                case EntitySelector.Type(ResourceLocation typeId) ->
                        types.computeIfAbsent(typeId, ignored -> new HashSet<>()).add(playerId);
            }
        }, () -> unrestricted.add(playerId));
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
