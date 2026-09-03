package io.zershyan.damagestats.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.handler.common.ServerLifecycleHandler;
import io.zershyan.damagestats.registry.packet.StatsInvalidatedPacket;
import io.zershyan.damagestats.stats.*;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.save.StatsExporter;
import io.zershyan.damagestats.stats.save.StatsStorage;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Stream;

/** GUI 出来之前的唯一查询入口，之后仍然保留 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class DSCommands {
    private static final int PERMISSION_GAMEMASTER = 2;
    private static final String ARG_ENTITY_TYPE = "entityType";

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("damagestats")
                .executes(context -> show(context, true))
                .then(Commands.literal("out").executes(context -> show(context, true)))
                .then(Commands.literal("in").executes(context -> show(context, false)))
                .then(Commands.literal("history").executes(DSCommands::history))
                .then(Commands.literal("type")
                        .then(Commands.argument(ARG_ENTITY_TYPE, ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.ENTITY_TYPE.keySet(), builder))
                                .executes(DSCommands::typeSummary)))
                .then(Commands.literal("toggle")
                        .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                        .executes(DSCommands::toggle))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                        .executes(DSCommands::reset))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                        .executes(DSCommands::reload))
                .then(Commands.literal("export")
                        .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                        .executes(DSCommands::export))
        );
    }

    private static int show(CommandContext<CommandSourceStack> context, boolean outgoing) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return 0;

        EntityRef self = EntityRef.of(player);
        StatsEntry entry = outgoing ? tracker.outgoing(self) : tracker.incoming(self);
        if(entry == null) {
            line(source, DSKeyLang.NoData.copy(), ChatFormatting.GRAY);
            return 0;
        }
        if(!DSConfig.TrackingEnabled.get()) line(source, DSKeyLang.TrackingDisabled.copy(), ChatFormatting.RED);

        long gameTime = player.level().getGameTime();
        line(source, (outgoing ? DSKeyLang.TitleOutgoing : DSKeyLang.TitleIncoming).copy(), ChatFormatting.GOLD);

        DamageSession session = entry.getCurrentSession();
        if(session != null) {
            line(source, DSKeyLang.SectionSession.copy(), ChatFormatting.GOLD);
            sendMetrics(source, tracker, session.getAccumulator(), session, gameTime);
            sendBreakdown(source, tracker, session.getAccumulator());
        }
        line(source, DSKeyLang.SectionLifetime.copy(), ChatFormatting.GOLD);
        sendMetrics(source, tracker, entry.getLifetime(), null, gameTime);
        sendBreakdown(source, tracker, entry.getLifetime());
        return 1;
    }

    /** 开关会写回配置文件，重启后仍然生效——不然玩家会以为自己关掉了但下次进来又在采集 */
    private static int toggle(CommandContext<CommandSourceStack> context) {
        boolean enabled = !DSConfig.TrackingEnabled.get();
        DSConfig.TrackingEnabled.set(enabled);
        DSConfig.TrackingEnabled.save();
        context.getSource().sendSuccess((enabled ? DSKeyLang.TrackingOn : DSKeyLang.TrackingOff)::copy, true);
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return 0;
        tracker.reset();
        ServerLifecycleHandler.persistReset(null);
        PacketDistributor.sendToAllPlayers(StatsInvalidatedPacket.INSTANCE);
        context.getSource().sendSuccess(DSKeyLang.StatsReset::copy, true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        DamageTypeCategories.load();
        context.getSource().sendSuccess(DSKeyLang.CategoriesReloaded::copy, true);
        return 1;
    }

    /**
     * 指令导出写到世界目录，因此要权限——那是服务器的磁盘。
     * 玩家导出自己的数据用界面上的按钮，写到他自己的游戏目录，不需要权限。
     */
    private static int export(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return 0;
        Path directory = StatsExporter.export(
                StatsViewBuilder.snapshotFor(tracker, player,
                        StatsFilter.fromSource(new EntitySelector.Instance(EntityRef.of(player)))),
                StatsStorage.directory(source.getServer()));
        if(directory == null) {
            line(source, DSKeyLang.ExportFailed.copy(), ChatFormatting.RED);
            return 0;
        }
        source.sendSuccess(() -> DSKeyLang.ExportDone.get(directory.toString()), true);
        return 1;
    }

    private static int history(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return 0;

        EntityRef self = EntityRef.of(player);
        StatsEntry outgoing = tracker.outgoing(self);
        StatsEntry incoming = tracker.incoming(self);
        boolean hasOutgoing = outgoing != null && !outgoing.getFinishedSessions().isEmpty();
        boolean hasIncoming = incoming != null && !incoming.getFinishedSessions().isEmpty();
        if(!hasOutgoing && !hasIncoming) {
            line(source, DSKeyLang.NoData.copy(), ChatFormatting.GRAY);
            return 0;
        }
        line(source, DSKeyLang.SectionHistory.copy(), ChatFormatting.GOLD);
        if(hasOutgoing) sendHistory(source, DSKeyLang.TitleOutgoing, outgoing);
        if(hasIncoming) sendHistory(source, DSKeyLang.TitleIncoming, incoming);
        return 1;
    }

    private static void sendHistory(CommandSourceStack source, MutableComponent title, StatsEntry entry) {
        line(source, title, ChatFormatting.GOLD);
        int index = 1;
        for (SessionSummary summary : entry.getFinishedSessions()) {
            line(source, DSKeyLang.SessionLine.getNumber2f(
                    index++,
                    summary.totalDamage(),
                    summary.averageDps(),
                    summary.durationSeconds(),
                    summary.hitCount()
            ), ChatFormatting.DARK_GRAY);
        }
    }

    /** 实体类型级的汇总，对应 R2.3 的「所有僵尸合并统计」 */
    private static int typeSummary(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        DamageTracker tracker = ServerStats.tracker();
        if(tracker == null) return 0;
        if(!DSConfig.PublicStats.get() && !source.hasPermission(PERMISSION_GAMEMASTER)) {
            line(source, DSKeyLang.StatsPrivate.copy(), ChatFormatting.RED);
            return 0;
        }

        ResourceLocation typeId = ResourceLocationArgument.getId(context, ARG_ENTITY_TYPE);
        StatsEntry out = tracker.outgoingByType(typeId);
        StatsEntry in = tracker.incomingByType(typeId);
        if(out == null && in == null) {
            line(source, DSKeyLang.NoDataForType.get(StatsNames.entityType(typeId)), ChatFormatting.GRAY);
            return 0;
        }
        long gameTime = source.getLevel().getGameTime();
        line(source, DSKeyLang.TypeSummary.get(StatsNames.entityType(typeId)), ChatFormatting.GOLD);
        if(out != null) {
            line(source, DSKeyLang.TitleOutgoing.copy(), ChatFormatting.GOLD);
            sendMetrics(source, tracker, out.getLifetime(), null, gameTime);
        }
        if(in != null) {
            line(source, DSKeyLang.TitleIncoming.copy(), ChatFormatting.GOLD);
            sendMetrics(source, tracker, in.getLifetime(), null, gameTime);
        }
        return 1;
    }

    private static void sendMetrics(CommandSourceStack source, DamageTracker tracker, DamageAccumulator acc,
                                    @Nullable DamageSession session, long gameTime) {
        line(source, DSKeyLang.TotalDamage.getNumber2f(acc.getTotalActual()), ChatFormatting.AQUA);
        line(source, DSKeyLang.AverageDps.getNumber2f(acc.getAverageDps()), ChatFormatting.AQUA);
        // 实时 DPS 依赖滑动窗口，只有还在进行中的会话才有
        if(session != null) line(source, DSKeyLang.RealtimeDps.getNumber2f(
                session.getRealtimeDps(gameTime, DSConfig.DpsWindowTicks.get())), ChatFormatting.AQUA);
        line(source, DSKeyLang.HitCount.get(acc.getHitCount()), ChatFormatting.DARK_GRAY);
        line(source, DSKeyLang.AverageDamage.getNumber2f(acc.getAverageDamage()), ChatFormatting.DARK_GRAY);
        EntityRef directSource = acc.getMaxSingleDirectSource();
        Component directSourceName = directSource == null
                ? Component.literal("-")
                : StatsNames.opponent(tracker, directSource);
        line(source, DSKeyLang.MaxSingle.getNumber2f(
                acc.getMaxSingle(),
                StatsNames.damageType(acc.getMaxSingleDamageType()),
                directSourceName,
                acc.getMaxSingleTime()), ChatFormatting.DARK_GRAY);
        line(source, DSKeyLang.MinSingle.getNumber2f(acc.getMinSingle()), ChatFormatting.DARK_GRAY);
        line(source, DSKeyLang.OriginalDamage.getNumber2f(acc.getTotalOriginal()), ChatFormatting.DARK_GRAY);
        line(source, DSKeyLang.ReductionRate.getNumber2f(acc.getReducedDamage(),
                acc.getReductionRate() * 100), ChatFormatting.DARK_GRAY);
        line(source, DSKeyLang.Duration.getNumber1f(
                acc.getDurationTicks() / DamageAccumulator.TICKS_PER_SECOND), ChatFormatting.DARK_GRAY);
        if(acc.getKillCount() > 0) line(source, DSKeyLang.KillCount.get(acc.getKillCount()), ChatFormatting.DARK_GRAY);
    }

    /** 三个筛选维度各来一份分解：伤害类型、直接来源、对手。都按伤害量降序，占比以实际伤害为分母 */
    private static void sendBreakdown(CommandSourceStack source, DamageTracker tracker, DamageAccumulator acc) {
        float total = acc.getTotalActual();
        if(total <= 0) return;
        line(source, DSKeyLang.SectionTypes.copy(), ChatFormatting.GOLD);
        sortedByDamage(acc.getByDamageType()).forEach(group ->
                detail(source, StatsNames.damageType(group.getKey()), group.getValue(), total));
        line(source, DSKeyLang.SectionSources.copy(), ChatFormatting.GOLD);
        sortedByDamage(acc.getByDirectSourceType()).forEach(group ->
                detail(source, StatsNames.entityType(group.getKey()), group.getValue(), total));
        line(source, DSKeyLang.SectionOpponents.copy(), ChatFormatting.GOLD);
        sortedByDamage(acc.getByOpponent()).forEach(group ->
                detail(source, StatsNames.opponent(tracker, group.getKey()), group.getValue(), total));
    }

    private static void detail(CommandSourceStack source, Component name, DamageAccumulator group, float total) {
        line(source, DSKeyLang.DetailLine.getNumber2f(
                name,
                group.getTotalActual(),
                group.getTotalActual() / total * 100,
                group.getHitCount()
        ), ChatFormatting.DARK_GRAY);
    }

    private static <K> Stream<Map.Entry<K, DamageAccumulator>> sortedByDamage(Map<K, DamageAccumulator> groups) {
        return groups.entrySet().stream().sorted(Comparator.comparingDouble(
                (Map.Entry<K, DamageAccumulator> group) -> group.getValue().getTotalActual()).reversed());
    }

    private static void line(CommandSourceStack source, MutableComponent text, ChatFormatting color) {
        MutableComponent styled = text.withStyle(color);
        source.sendSuccess(() -> styled, false);
    }
}
