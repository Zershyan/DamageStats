package io.zershyan.damagestats.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.save.StatsExporter;
import io.zershyan.damagestats.stats.save.StatsStorage;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

public final class ExportCommand {
    private static final int PERMISSION_GAMEMASTER = 2;

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.literal("export")
                .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                .executes(ExportCommand::execute));
    }

    /**
     * 指令导出写到世界目录，因此要权限——那是服务器的磁盘。
     * 玩家导出自己的数据用界面上的按钮，写到他自己的游戏目录，不需要权限。
     */
    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
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
}
