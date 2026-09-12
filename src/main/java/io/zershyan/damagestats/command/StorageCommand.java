package io.zershyan.damagestats.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.zershyan.damagestats.registry.packet.StorageOverviewPacket;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.save.StatsStorage;
import io.zershyan.damagestats.stats.save.StorageMaintenance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class StorageCommand {
    private static final int PERMISSION_GAMEMASTER = 2;

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.literal("storage")
                .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                .executes(StorageCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        MinecraftServer server = player.getServer();
        if(server == null) return 0;
        PacketDistributor.sendToPlayer(player, StorageOverviewPacket.open(StorageMaintenance.overview(
                StatsStorage.directory(server), ServerStats.journal())));
        return 1;
    }
}
