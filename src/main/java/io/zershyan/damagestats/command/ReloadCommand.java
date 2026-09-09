package io.zershyan.damagestats.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.zershyan.damagestats.config.DamageTypeCategories;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.ServerStats;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ReloadCommand {
    private static final int PERMISSION_GAMEMASTER = 2;

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.literal("reload")
                .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                .executes(ReloadCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        DamageTypeCategories.load();
        if(ServerStats.journal() != null) ServerStats.journal().invalidateDamageTypeCategoryCache();
        context.getSource().sendSuccess(DSKeyLang.CategoriesReloaded::copy, true);
        return 1;
    }
}
