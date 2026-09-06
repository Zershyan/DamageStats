package io.zershyan.damagestats.registry.packet;

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
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/** 请求一个实体类型页，或指定类型下的实例页；游标只能前进且每页最多 50 条。 */
public record EntityChoiceRequestPacket(
        FocusSelectionSlot slot,
        Optional<ResourceLocation> typeFilter,
        String search,
        int cursor,
        int requestId,
        StatsFilter contextFilter
) implements CustomPacketPayload {
    private static final int MAX_SEARCH_LENGTH = 64;

    private record QueryContribution(float damage, int hitCount) {
        private QueryContribution add(float additionalDamage) {
            return new QueryContribution(damage + additionalDamage, hitCount + 1);
        }
    }

    public static final Type<EntityChoiceRequestPacket> TYPE = new Type<>(DamageStats.id("entity_choices"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EntityChoiceRequestPacket> STREAM_CODEC = StreamCodec.composite(
            FocusSelectionSlot.STREAM_CODEC, EntityChoiceRequestPacket::slot,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), EntityChoiceRequestPacket::typeFilter,
            ByteBufCodecs.STRING_UTF8, EntityChoiceRequestPacket::search,
            ByteBufCodecs.VAR_INT, EntityChoiceRequestPacket::cursor,
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
            boolean allowed = switch (payload.slot()) {
                case TARGET -> manager.canBrowse(player, withoutSlot(payload.contextFilter(), FocusSelectionSlot.TARGET), tracker);
                case SOURCE -> player.hasPermissions(2)
                        && manager.canBrowse(player, withoutSlot(payload.contextFilter(), FocusSelectionSlot.SOURCE), tracker);
                case DIRECT_SOURCE -> payload.typeFilter().isPresent()
                        && manager.canBrowse(player, payload.contextFilter(), tracker);
            };
            allowed &= payload.typeFilter().isEmpty()
                    || validTypeFilter(payload.slot(), payload.typeFilter().get(), tracker);
            long focusVersion = manager.focusFor(player).version();
            if(!allowed) {
                PacketDistributor.sendToPlayer(player, new EntityChoicePagePacket(new EntityChoicePage(
                        payload.slot(), focusVersion, payload.requestId(), false, payload.typeFilter(), List.of(), false)));
                return;
            }
            String search = payload.search().length() > MAX_SEARCH_LENGTH
                    ? payload.search().substring(0, MAX_SEARCH_LENGTH)
                    : payload.search();
            List<EntityChoiceView> entries = payload.slot() == FocusSelectionSlot.DIRECT_SOURCE
                    ? directSourceChoices(tracker, payload.typeFilter().orElseThrow(), search, payload.contextFilter())
                    : payload.typeFilter().map(typeId -> instanceChoices(tracker, typeId, search,
                                    payload.slot(), payload.contextFilter()))
                            .orElseGet(() -> typeChoices(tracker, search, payload.slot()));
            int cursor = Math.clamp(payload.cursor(), 0, entries.size());
            int end = Math.min(entries.size(), cursor + EntityChoicePage.PAGE_SIZE);
            PacketDistributor.sendToPlayer(player, new EntityChoicePagePacket(new EntityChoicePage(
                    payload.slot(), focusVersion, payload.requestId(), true, payload.typeFilter(), entries.subList(cursor, end), end < entries.size())));
        });
    }

    private static List<EntityChoiceView> typeChoices(DamageTracker tracker, String search, FocusSelectionSlot slot) {
        String normalized = search.toLowerCase(Locale.ROOT);
        Set<ResourceLocation> recordedTypes = recordedTypes(slot);
        return (slot == FocusSelectionSlot.TARGET
                ? BuiltInRegistries.ENTITY_TYPE.entrySet().stream()
                        .filter(entry -> LivingEntity.class.isAssignableFrom(entry.getValue().getBaseClass()))
                        .map(entry -> BuiltInRegistries.ENTITY_TYPE.getKey(entry.getValue()))
                : recordedTypes.stream())
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .filter(typeId -> slot != FocusSelectionSlot.SOURCE || isLivingType(typeId))
                .filter(typeId -> matches(StatsNames.entityType(typeId), typeId.toString(), normalized))
                .map(typeId -> new EntityChoiceView(new EntitySelector.Type(typeId), StatsNames.entityType(typeId), Component.empty()))
                .toList();
    }

    private static List<EntityChoiceView> instanceChoices(DamageTracker tracker, ResourceLocation typeId, String search,
                                                           FocusSelectionSlot slot, StatsFilter contextFilter) {
        if(!validTypeFilter(slot, typeId, tracker)) return List.of();
        String normalized = search.toLowerCase(Locale.ROOT);
        Map<UUID, QueryContribution> contributions = queryContributions(slot, contextFilter);
        return tracker.instanceDirectory().entries().stream()
                .filter(metadata -> typeId.equals(metadata.ref().typeId()))
                .filter(metadata -> matches(StatsNames.opponent(tracker, metadata.ref()), metadata.displayName(), normalized))
                .map(metadata -> new EntityChoiceView(new EntitySelector.Instance(metadata.ref()),
                        displayName(tracker, metadata), detail(metadata,
                                contributions.getOrDefault(metadata.ref().id(), new QueryContribution(0, 0)))))
                .sorted(Comparator.comparingDouble((EntityChoiceView entry) -> contribution(entry, contributions)).reversed()
                        .thenComparing(EntityChoiceView::name, Comparator.comparing(Component::getString)))
                .toList();
    }

    private static List<EntityChoiceView> directSourceChoices(DamageTracker tracker, ResourceLocation typeId,
                                                               String search, StatsFilter contextFilter) {
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return List.of();
        Map<UUID, QueryContribution> contributions = queryContributions(FocusSelectionSlot.DIRECT_SOURCE, contextFilter);
        String normalized = search.toLowerCase(Locale.ROOT);
        return tracker.instanceDirectory().entries().stream()
                .filter(metadata -> typeId.equals(metadata.ref().typeId()))
                .filter(metadata -> contributions.containsKey(metadata.ref().id()))
                .filter(metadata -> matches(StatsNames.opponent(tracker, metadata.ref()), metadata.displayName(), normalized))
                .map(metadata -> new EntityChoiceView(new EntitySelector.Instance(metadata.ref()),
                        displayName(tracker, metadata), detail(metadata,
                                contributions.get(metadata.ref().id()))))
                .sorted(Comparator.comparingDouble((EntityChoiceView entry) -> contribution(entry, contributions)).reversed()
                        .thenComparing(EntityChoiceView::name, Comparator.comparing(Component::getString)))
                .toList();
    }

    private static Map<UUID, QueryContribution> queryContributions(FocusSelectionSlot slot, StatsFilter contextFilter) {
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return Map.of();
        Map<UUID, QueryContribution> contributions = new HashMap<>();
        journal.forEachMatching(withoutSlot(contextFilter, slot), record -> {
            UUID id = switch (slot) {
                case SOURCE -> (contextFilter.sourceIsDirectSource() ? record.directSource() : record.source()).id();
                case TARGET -> record.target().id();
                case DIRECT_SOURCE -> record.directSource().id();
            };
            contributions.merge(id, new QueryContribution(record.actualDamage(), 1),
                    (left, right) -> left.add(right.damage()));
        });
        return contributions;
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

    private static float contribution(EntityChoiceView entry, Map<UUID, QueryContribution> contributions) {
        if(!(entry.selector() instanceof EntitySelector.Instance instance)) return 0;
        return contributions.getOrDefault(instance.ref().id(), new QueryContribution(0, 0)).damage();
    }

    private static boolean matches(Component name, String fallback, String search) {
        if(search.isEmpty()) return true;
        return name.getString().toLowerCase(Locale.ROOT).contains(search)
                || fallback.toLowerCase(Locale.ROOT).contains(search);
    }

    private static Component detail(InstanceMetadata metadata, QueryContribution contribution) {
        Component location = Component.literal(metadata.dimensionId() + " " + metadata.blockX() + ", "
                + metadata.blockY() + ", " + metadata.blockZ());
        MutableComponent detail = DSKeyLang.InstanceLastInteraction.get(metadata.lastInteractionGameTime(), location);
        if(contribution.hitCount() == 0) return detail;
        return detail.append(Component.literal("  ")).append(DSKeyLang.InstanceQueryStats.getNumber1f(
                contribution.damage(), contribution.hitCount()));
    }

    private static Component displayName(DamageTracker tracker, InstanceMetadata metadata) {
        if(!metadata.displayName().isEmpty()) return Component.literal(metadata.displayName());
        return StatsNames.opponent(tracker, metadata.ref());
    }

    private static Set<ResourceLocation> recordedTypes(FocusSelectionSlot slot) {
        DamageEventJournal journal = ServerStats.journal();
        if(journal == null) return Set.of();
        return switch (slot) {
            case SOURCE -> journal.recordedSourceTypes();
            case TARGET -> journal.recordedTargetTypes();
            case DIRECT_SOURCE -> journal.recordedDirectSourceTypes();
        };
    }

    private static boolean validTypeFilter(FocusSelectionSlot slot, ResourceLocation typeId, DamageTracker tracker) {
        if(slot == FocusSelectionSlot.TARGET) return isLivingType(typeId);
        if(slot == FocusSelectionSlot.SOURCE) {
            DamageEventJournal journal = ServerStats.journal();
            return isLivingType(typeId) && journal != null && journal.recordedSourceTypes().contains(typeId);
        }
        DamageEventJournal journal = ServerStats.journal();
        return journal != null && journal.recordedDirectSourceTypes().contains(typeId);
    }

    private static boolean isLivingType(ResourceLocation typeId) {
        var type = BuiltInRegistries.ENTITY_TYPE.get(typeId);
        return type != null && LivingEntity.class.isAssignableFrom(type.getBaseClass());
    }
}
