package io.zershyan.damagestats.registry.packet;

import com.mojang.logging.LogUtils;
import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.InstanceMetadata;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import io.zershyan.damagestats.stats.focus.StatsFocusManager;
import io.zershyan.damagestats.stats.save.DamageEventJournal;
import io.zershyan.damagestats.stats.view.EntityChoicePage;
import io.zershyan.damagestats.stats.view.EntityChoiceView;
import io.zershyan.damagestats.util.EntityTypeHelper;
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 请求一个实体类型页，或指定类型下的实例页；游标绑定服务端快照且每页最多 50 条。 */
public record EntityChoiceRequestPacket(
        FocusSelectionSlot slot,
        Optional<ResourceLocation> typeFilter,
        String search,
        String cursor,
        int requestId,
        StatsFilter contextFilter
) implements CustomPacketPayload {
    private static final int MAX_SEARCH_LENGTH = 64;
    private static final int MAX_CURSOR_LENGTH = 128;
    private static final int MAX_CURSORS = 64;
    private static final ResourceLocation PLAYER_TYPE = ResourceLocation.withDefaultNamespace("player");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<DamageEventJournal,
            LinkedHashMap<String, ChoiceCursor>> CHOICE_CURSORS =
            new WeakHashMap<>();

    private static long nextSnapshotId;

    private record ChoiceCursor(
            UUID owner,
            @Nullable DamageEventJournal journal,
            long queryRevision,
            long directoryRevision,
            long focusVersion,
            FocusSelectionSlot slot,
            Optional<ResourceLocation> typeFilter,
            String search,
            StatsFilter contextFilter,
            long snapshotId,
            List<EntityChoiceView> entries,
            int start
    ) {
        private ChoiceCursor at(int newStart) {
            return new ChoiceCursor(owner, journal, queryRevision, directoryRevision, focusVersion, slot,
                    typeFilter, search, contextFilter, snapshotId, entries, newStart);
        }
    }

    private record ChoiceSnapshot(List<InstanceMetadata> instances, Map<UUID, String> names) {}

    public static final Type<EntityChoiceRequestPacket> TYPE = new Type<>(DamageStats.id("entity_choices"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityChoiceRequestPacket> STREAM_CODEC = StreamCodec.composite(
            FocusSelectionSlot.STREAM_CODEC, EntityChoiceRequestPacket::slot,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), EntityChoiceRequestPacket::typeFilter,
            ByteBufCodecs.STRING_UTF8, EntityChoiceRequestPacket::search,
            ByteBufCodecs.STRING_UTF8, EntityChoiceRequestPacket::cursor,
            ByteBufCodecs.VAR_INT, EntityChoiceRequestPacket::requestId,
            StatsFilter.STREAM_CODEC, EntityChoiceRequestPacket::contextFilter,
            EntityChoiceRequestPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EntityChoiceRequestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            StatsFocusManager manager = ServerStats.focusManager();
            if(tracker == null || manager == null) return;
            DamageEventJournal journal = ServerStats.journal();
            if(!isAllowed(payload, player, tracker, manager, journal)) {
                sendDenied(player, payload, manager.focusFor(player).version());
                return;
            }
            long focusVersion = manager.focusFor(player).version();
            String search = payload.search().length() > MAX_SEARCH_LENGTH
                    ? payload.search().substring(0, MAX_SEARCH_LENGTH)
                    : payload.search();
            MinecraftServer server = player.getServer();
            if(server == null) return;
            if(journal != null && !payload.cursor().isEmpty()) {
                ChoiceCursor page = findCursor(journal, payload.cursor(), player, tracker, manager, payload, search);
                if(page != null) {
                    sendPage(player, payload, focusVersion, page, payload.cursor());
                    return;
                }
                sendPage(player, payload, focusVersion, null, "");
                return;
            }
            ChoiceSnapshot snapshot = new ChoiceSnapshot(List.copyOf(tracker.instanceDirectory().entries()),
                    tracker.cachedNames());
            boolean fullAccess = StatsFocusManager.hasFullAccess(player);
            CompletableFuture.supplyAsync(() -> buildEntries(journal, payload.slot(), payload.typeFilter(), search,
                            payload.contextFilter(), snapshot, fullAccess))
                    .whenComplete((entries, error) -> server.execute(() -> finishRequest(player, server, tracker,
                            manager, journal, payload, search, entries, error)));
        });
    }

    private static boolean isAllowed(EntityChoiceRequestPacket payload, ServerPlayer player,
                                     DamageTracker tracker, StatsFocusManager manager,
                                     @Nullable DamageEventJournal journal) {
        boolean allowed = switch (payload.slot()) {
            case TARGET -> manager.canBrowse(player, withoutSlot(payload.contextFilter(), FocusSelectionSlot.TARGET), tracker);
            case SOURCE -> manager.canBrowse(player,
                    withoutSlot(payload.contextFilter(), FocusSelectionSlot.SOURCE), tracker);
            case DIRECT_SOURCE -> payload.typeFilter().isPresent()
                    && manager.canBrowse(player, payload.contextFilter(), tracker);
        };
        return allowed && (payload.typeFilter().isEmpty()
                || validTypeFilter(journal, StatsFocusManager.hasFullAccess(player),
                payload.slot(), payload.typeFilter().get()));
    }

    private static void sendDenied(ServerPlayer player, EntityChoiceRequestPacket payload, long focusVersion) {
        PacketDistributor.sendToPlayer(player, new EntityChoicePagePacket(new EntityChoicePage(
                payload.slot(), focusVersion, payload.requestId(), 0, "", "", false,
                payload.typeFilter(), List.of(), false)));
    }

    private static void finishRequest(ServerPlayer player, MinecraftServer server, DamageTracker tracker,
                                      StatsFocusManager manager, @Nullable DamageEventJournal journal,
                                      EntityChoiceRequestPacket payload, String search,
                                      @Nullable List<EntityChoiceView> entries, @Nullable Throwable error) {
        if(error != null) {
            LOGGER.error("构造实体选择列表失败", error);
            return;
        }
        if(entries == null || ServerStats.tracker() != tracker || ServerStats.journal() != journal
                || player.isRemoved() || server.getPlayerList().getPlayer(player.getUUID()) != player) return;
        if(!isAllowed(payload, player, tracker, manager, journal)) {
            sendDenied(player, payload, manager.focusFor(player).version());
            return;
        }
        long currentFocusVersion = manager.focusFor(player).version();
        ChoiceCursor page = new ChoiceCursor(player.getUUID(), journal,
                journal == null ? 0 : journal.queryRevision(), tracker.instanceDirectory().revision(),
                currentFocusVersion, payload.slot(), payload.typeFilter(), search,
                payload.contextFilter(), ++nextSnapshotId, List.copyOf(entries), 0);
        String currentCursor = journal == null ? "" : UUID.randomUUID().toString();
        if(!currentCursor.isEmpty()) storeCursor(currentCursor, page);
        sendPage(player, payload, currentFocusVersion, page, currentCursor);
    }

    private static void sendPage(ServerPlayer player, EntityChoiceRequestPacket payload, long focusVersion,
                                 @Nullable ChoiceCursor page, String currentCursor) {
        if(page == null) {
            PacketDistributor.sendToPlayer(player, new EntityChoicePagePacket(new EntityChoicePage(
                    payload.slot(), focusVersion, payload.requestId(), 0, "", "", true,
                    payload.typeFilter(), List.of(), false)));
            return;
        }
        List<EntityChoiceView> entries = page.entries();
        int start = Math.clamp(page.start(), 0, entries.size());
        int end = Math.min(entries.size(), start + EntityChoicePage.PAGE_SIZE);
        String nextCursor = "";
        if(end < entries.size() && page.journal() != null) {
            nextCursor = UUID.randomUUID().toString();
            storeCursor(nextCursor, page.at(end));
        }
        PacketDistributor.sendToPlayer(player, new EntityChoicePagePacket(new EntityChoicePage(
                page.slot(), focusVersion, payload.requestId(), page.snapshotId(), currentCursor, nextCursor, true,
                page.typeFilter(), entries.subList(start, end), !nextCursor.isEmpty())));
    }

    private static @Nullable ChoiceCursor findCursor(DamageEventJournal journal, String cursor, ServerPlayer player,
                                                     DamageTracker tracker, StatsFocusManager manager,
                                                     EntityChoiceRequestPacket payload, String search) {
        if(cursor.length() > MAX_CURSOR_LENGTH) return null;
        ChoiceCursor page;
        synchronized (CHOICE_CURSORS) {
            LinkedHashMap<String, ChoiceCursor> cursors = CHOICE_CURSORS.get(journal);
            page = cursors == null ? null : cursors.get(cursor);
        }
        if(page == null || !page.owner().equals(player.getUUID())) return null;
        if(page.journal() != journal || page.queryRevision() != journal.queryRevision()
                || page.directoryRevision() != tracker.instanceDirectory().revision()
                || page.focusVersion() != manager.focusFor(player).version()
                || page.slot() != payload.slot() || !page.typeFilter().equals(payload.typeFilter())
                || !page.search().equals(search) || !page.contextFilter().equals(payload.contextFilter())) return null;
        return page;
    }

    private static void storeCursor(String cursor, ChoiceCursor page) {
        synchronized (CHOICE_CURSORS) {
            LinkedHashMap<String, ChoiceCursor> cursors = CHOICE_CURSORS.computeIfAbsent(page.journal(), ignored ->
                    new LinkedHashMap<>(16, 0.75f, true) {
                        @Override
                        protected boolean removeEldestEntry(Map.Entry<String, ChoiceCursor> eldest) {
                            return size() > MAX_CURSORS;
                        }
                    });
            cursors.put(cursor, page);
        }
    }

    private static List<EntityChoiceView> buildEntries(@Nullable DamageEventJournal journal,
                                                       FocusSelectionSlot slot,
                                                       Optional<ResourceLocation> typeFilter, String search,
                                                       StatsFilter contextFilter, ChoiceSnapshot snapshot,
                                                       boolean fullAccess) {
        return slot == FocusSelectionSlot.DIRECT_SOURCE
                ? directSourceChoices(journal, typeFilter.orElseThrow(), search, contextFilter, snapshot)
                : typeFilter.map(typeId -> instanceChoices(journal, typeId, search, slot, contextFilter,
                        snapshot, fullAccess))
                        .orElseGet(() -> typeChoices(journal, search, slot, contextFilter, fullAccess));
    }

    private static List<EntityChoiceView> typeChoices(@Nullable DamageEventJournal journal, String search,
                                                       FocusSelectionSlot slot, StatsFilter contextFilter,
                                                       boolean fullAccess) {
        String normalized = search.toLowerCase(Locale.ROOT);
        Map<ResourceLocation, Long> recent = recentTypeTimes(journal, slot, contextFilter);
        Set<ResourceLocation> recorded = recent.keySet();
        Set<ResourceLocation> candidates = new HashSet<>(recorded);
        if(slot == FocusSelectionSlot.SOURCE && fullAccess) {
            candidates.add(PLAYER_TYPE);
        }
        // 空搜索只展示近期出现过的类型；输入关键词后再展开完整生物注册表。
        if(!normalized.isEmpty()
                && (slot == FocusSelectionSlot.TARGET || slot == FocusSelectionSlot.SOURCE && fullAccess)) {
            BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                    .map(entry -> BuiltInRegistries.ENTITY_TYPE.getKey(entry.getValue()))
                    .filter(EntityTypeHelper::isLivingType)
                    .forEach(candidates::add);
        }
        return candidates.stream()
                .filter(typeId -> slot != FocusSelectionSlot.SOURCE || isLivingType(typeId))
                .filter(typeId -> matches(StatsNames.entityType(typeId), typeId.toString(), normalized))
                .sorted(Comparator.comparingLong((ResourceLocation typeId) -> recent.getOrDefault(typeId, Long.MIN_VALUE))
                        .reversed().thenComparing(ResourceLocation::toString))
                .map(typeId -> new EntityChoiceView(new EntitySelector.Type(typeId), StatsNames.entityType(typeId), Component.empty()))
                .toList();
    }

    private static Map<ResourceLocation, Long> recentTypeTimes(@Nullable DamageEventJournal journal,
                                                                FocusSelectionSlot slot,
                                                                StatsFilter contextFilter) {
        if(journal == null) return Map.of();
        StatsFilter queryFilter = withoutSlot(contextFilter, slot);
        Map<ResourceLocation, Long> result = new HashMap<>();
        journal.query(queryFilter).records().forEach(record -> {
            ResourceLocation typeId = switch (slot) {
                case SOURCE -> contextFilter.sourceIsDirectSource()
                        ? record.directSource().typeIdOrEnvironment() : record.source().typeIdOrEnvironment();
                case TARGET -> record.target().typeIdOrEnvironment();
                case DIRECT_SOURCE -> record.directSource().typeIdOrEnvironment();
            };
            result.merge(typeId, record.gameTime(), Math::max);
        });
        return result;
    }

    private static List<EntityChoiceView> instanceChoices(@Nullable DamageEventJournal journal,
                                                           ResourceLocation typeId, String search,
                                                           FocusSelectionSlot slot, StatsFilter contextFilter,
                                                           ChoiceSnapshot snapshot, boolean fullAccess) {
        if(!validTypeFilter(journal, fullAccess, slot, typeId)) return List.of();
        String normalized = search.toLowerCase(Locale.ROOT);
        if(journal == null) return List.of();
        StatsFilter queryFilter = withoutSlot(contextFilter, slot);
        DamageEventJournal.ContributionDimension dimension = contributionDimension(slot, contextFilter);
        DamageEventJournal.QueryResult query = journal.query(queryFilter);
        return instanceEntries(typeId, normalized, dimension, query, snapshot, false);
    }

    private static List<EntityChoiceView> instanceEntries(
            ResourceLocation typeId, String normalized, DamageEventJournal.ContributionDimension dimension,
            DamageEventJournal.QueryResult query, ChoiceSnapshot snapshot, boolean contributingOnly) {
        return buildInstanceEntries(typeId, normalized, query.contributions(dimension), snapshot, contributingOnly);
    }

    private static List<EntityChoiceView> buildInstanceEntries(
            ResourceLocation typeId, String normalized, Map<UUID, DamageEventJournal.DamageContribution> contributions,
            ChoiceSnapshot snapshot, boolean contributingOnly) {
        Component fallbackName = StatsNames.entityType(typeId);
        return snapshot.instances().stream()
                .filter(metadata -> typeId.equals(metadata.ref().typeId()))
                .filter(metadata -> !contributingOnly || contributions.containsKey(metadata.ref().id()))
                .filter(metadata -> matches(displayName(snapshot, metadata, fallbackName), metadata.displayName(), normalized))
                .map(metadata -> new EntityChoiceView(new EntitySelector.Instance(metadata.ref()),
                        displayName(snapshot, metadata, fallbackName), detail(metadata,
                                contributions.getOrDefault(metadata.ref().id(),
                                        new DamageEventJournal.DamageContribution(0, 0)))))
                .sorted(Comparator.comparingDouble((EntityChoiceView entry) -> contribution(entry, contributions)).reversed()
                        .thenComparing(EntityChoiceView::name, Comparator.comparing(Component::getString)))
                .toList();
    }

    private static List<EntityChoiceView> directSourceChoices(@Nullable DamageEventJournal journal,
                                                               ResourceLocation typeId, String search,
                                                               StatsFilter contextFilter, ChoiceSnapshot snapshot) {
        if(journal == null) return List.of();
        String normalized = search.toLowerCase(Locale.ROOT);
        StatsFilter queryFilter = withoutSlot(contextFilter, FocusSelectionSlot.DIRECT_SOURCE);
        return instanceEntries(typeId, normalized, DamageEventJournal.ContributionDimension.DIRECT_SOURCE,
                journal.query(queryFilter), snapshot, true);
    }

    private static DamageEventJournal.ContributionDimension contributionDimension(
            FocusSelectionSlot slot, StatsFilter contextFilter) {
        return switch (slot) {
            case SOURCE -> contextFilter.sourceIsDirectSource()
                    ? DamageEventJournal.ContributionDimension.DIRECT_SOURCE
                    : DamageEventJournal.ContributionDimension.SOURCE;
            case TARGET -> DamageEventJournal.ContributionDimension.TARGET;
            case DIRECT_SOURCE -> DamageEventJournal.ContributionDimension.DIRECT_SOURCE;
        };
    }

    private static StatsFilter withoutSlot(StatsFilter filter, FocusSelectionSlot slot) {
        return switch (slot) {
            case SOURCE -> new StatsFilter(Optional.empty(), filter.target(), filter.directSource(),
                    filter.damageType(), false);
            case TARGET -> new StatsFilter(filter.source(), Optional.empty(), filter.directSource(),
                    filter.damageType(), filter.sourceIsDirectSource());
            case DIRECT_SOURCE -> new StatsFilter(filter.source(), filter.target(), Optional.empty(),
                    filter.damageType(), filter.sourceIsDirectSource());
        };
    }

    private static float contribution(EntityChoiceView entry,
                                      Map<UUID, DamageEventJournal.DamageContribution> contributions) {
        if(!(entry.selector() instanceof EntitySelector.Instance instance)) return 0;
        return contributions.getOrDefault(instance.ref().id(),
                new DamageEventJournal.DamageContribution(0, 0)).damage();
    }

    private static boolean matches(Component name, String fallback, String search) {
        if(search.isEmpty()) return true;
        return name.getString().toLowerCase(Locale.ROOT).contains(search)
                || fallback.toLowerCase(Locale.ROOT).contains(search);
    }

    private static Component detail(InstanceMetadata metadata, DamageEventJournal.DamageContribution contribution) {
        Component location = Component.literal(metadata.dimensionId() + " " + metadata.blockX() + ", "
                + metadata.blockY() + ", " + metadata.blockZ());
        MutableComponent detail = DSKeyLang.InstanceLastInteraction.get(metadata.lastInteractionGameTime(), location);
        if(contribution.hitCount() == 0) return detail;
        return detail.append(Component.literal("  ")).append(DSKeyLang.InstanceQueryStats.getNumber1f(
                contribution.damage(), contribution.hitCount()));
    }

    private static Component displayName(ChoiceSnapshot snapshot, InstanceMetadata metadata,
                                         Component fallbackName) {
        Component name;
        if(!metadata.displayName().isEmpty()) name = Component.literal(metadata.displayName());
        else {
            String cached = snapshot.names().get(metadata.ref().id());
            name = cached == null || cached.isBlank() ? fallbackName : Component.literal(cached);
        }
        if(metadata.instanceId() <= 0) return name;
        return Component.literal("#" + metadata.instanceId() + " ").append(name);
    }

    private static Set<ResourceLocation> recordedTypes(FocusSelectionSlot slot) {
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return Set.of();
        return switch (slot) {
            case SOURCE -> journal.recordedSourceTypesIncludingDirect();
            case TARGET -> journal.recordedTargetTypes();
            case DIRECT_SOURCE -> journal.recordedDirectSourceTypes();
        };
    }

    private static boolean validTypeFilter(@Nullable DamageEventJournal journal, boolean fullAccess,
                                           FocusSelectionSlot slot, ResourceLocation typeId) {
        if(slot == FocusSelectionSlot.TARGET) return isLivingType(typeId);
        if(slot == FocusSelectionSlot.SOURCE) {
            return isLivingType(typeId)
                    && (fullAccess
                    || journal != null && journal.recordedSourceTypesIncludingDirect().contains(typeId));
        }
        return journal != null && journal.recordedDirectSourceTypes().contains(typeId);
    }

    private static boolean isLivingType(ResourceLocation typeId) {
        return EntityTypeHelper.isLivingType(typeId);
    }
}
