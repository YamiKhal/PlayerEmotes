package com.yamikhal.playeremotes.server;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

// /playeremotes reload re-reads the server config
public final class ServerCommands {

    private ServerCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("playeremotes")
                .requires(source -> EmotePermissions.has(source, EmotePermissions.Node.RELOAD))
                .then(Commands.literal("reload").executes(context -> {
                    ServerConfig.reload();
                    EmoteTracker.resendRules(context.getSource().getServer().getPlayerList().getPlayers());
                    context.getSource().sendSuccess(() -> Component.literal("PlayerEmotes server config reloaded"), true);
                    return 1;
                })));
    }
}
