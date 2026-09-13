package io.zershyan.damagestats.registry;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.zershyan.damagestats.command.ExportCommand;
import io.zershyan.damagestats.command.ReloadCommand;
import io.zershyan.damagestats.command.StorageCommand;
import io.zershyan.damagestats.command.ToggleCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** 注册 /damagestats 管理命令及其子命令。 */
public final class DSCommands {
    public static void doRegister(IEventBus forgeEventBus) {
        forgeEventBus.addListener(DSCommands::commonCommandRegister);
    }

    public static void commonCommandRegister(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("damagestats");
        ToggleCommand.register(builder);
        ReloadCommand.register(builder);
        ExportCommand.register(builder);
        StorageCommand.register(builder);
        event.getDispatcher().register(builder);
    }
}
