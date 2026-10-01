package com.darkgreen_world.linkart.utility;

import com.darkgreen_world.linkart.Linkart;
import com.darkgreen_world.linkart.configuration.LinkartConfiguration;
import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Supplier;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class LinkartCommand {

    private static final Supplier<Component> RELOADED = () -> Component.literal("reloaded linkart config");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("linkart")
                .then(Commands.literal("config")
                        .then(Commands.literal("reload")
                                .executes(context -> {
                                    LinkartConfiguration.load();
                                    context.getSource().sendSuccess(RELOADED, true);
                                    return 1;
                                }))));
    }
}
