package io.zershyan.damagestats.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.StorageOverviewPacket;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.save.StatsExporter;
import io.zershyan.damagestats.stats.save.StatsStorage;
import io.zershyan.damagestats.stats.save.StorageMaintenance;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.nio.file.Path;

/** 保留全局开关、配置重载、服务器导出和存储概览等管理入口，统计查询统一通过 GUI。 */
@EventBusSubscriber(modid = DamageStats.MODID)
public final class DSCommands {
    private static final int PERMISSION_GAMEMASTER = 2;

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("damagestats")
                .then(Commands.literal("toggle")
                        .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                        .executes(DSCommands::toggle))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                        .executes(DSCommands::reload))
                .then(Commands.literal("export")
                        .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                        .executes(DSCommands::export))
                .then(Commands.literal("storage")
                        .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                        .executes(DSCommands::storage))
        );
    }

    /** 开关会写回配置文件，重启后仍然生效——不然玩家会以为自己关掉了但下次进来又在采集 */
    private static int toggle(CommandContext<CommandSourceStack> context) {
        boolean enabled = !DSConfig.TrackingEnabled.get();
        DSConfig.TrackingEnabled.set(enabled);
        DSConfig.TrackingEnabled.save();
        context.getSource().sendSuccess((enabled ? DSKeyLang.TrackingOn : DSKeyLang.TrackingOff)::copy, true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        DamageTypeCategories.load();
        if(ServerStats.journal() != null) ServerStats.journal().invalidateDamageTypeCategoryCache();
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
                StatsViewBuilder.snapshotFor(tracker, player, StatsFilter.NONE),
                StatsStorage.directory(source.getServer()));
        if(directory == null) {
            source.sendFailure(DSKeyLang.ExportFailed.copy());
            return 0;
        }
        source.sendSuccess(() -> DSKeyLang.ExportDone.get(directory.toString()), true);
        return 1;
    }

    private static int storage(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        PacketDistributor.sendToPlayer(player, StorageOverviewPacket.open(StorageMaintenance.overview(
                StatsStorage.directory(player.getServer()), ServerStats.journal())));
        return 1;
    }

}
