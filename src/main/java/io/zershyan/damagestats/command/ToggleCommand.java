package io.zershyan.damagestats.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class ToggleCommand {
    private static final int PERMISSION_GAMEMASTER = 2;

    public static void register(LiteralArgumentBuilder<CommandSourceStack> builder) {
        builder.then(Commands.literal("toggle")
                .requires(source -> source.hasPermission(PERMISSION_GAMEMASTER))
                .executes(ToggleCommand::execute));
    }

    /** 开关会写回配置文件，重启后仍然生效，避免玩家误以为下次进入后仍会采集。 */
    private static int execute(CommandContext<CommandSourceStack> context) {
        boolean enabled = !DSConfig.TrackingEnabled.get();
        DSConfig.TrackingEnabled.set(enabled);
        DSConfig.TrackingEnabled.save();
        context.getSource().sendSuccess((enabled ? DSKeyLang.TrackingOn : DSKeyLang.TrackingOff)::copy, true);
        return 1;
    }
}
