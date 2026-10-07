package com.yamikhal.playeremotes.server;

import com.yamikhal.playeremotes.PlayerEmotes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

// loaders check the nodes through their permission API (LuckPerms and similar hook into it) and fall back to the
// vanilla operator level
public final class EmotePermissions {

    public enum Node {
        USE("use", 0),
        // joining another player's emote with /emotesync
        SYNC("sync", 0),
        // starting and joining two player emotes
        PARTNER("partner", 0),
        // emotes listed as restricted in the server config
        RESTRICTED("restricted", 2),
        RELOAD("command.reload", 3);

        // name without the mod id prefix
        public final String path;
        // grants the node when no permission plugin decides
        public final int opLevel;

        Node(String path, int opLevel) {
            this.path = path;
            this.opLevel = opLevel;
        }

        public String fullName() {
            return PlayerEmotes.MOD_ID + "." + this.path;
        }
    }

    private EmotePermissions() {}

    public static boolean has(ServerPlayer player, Node node) {
        return PlayerEmotes.platform().hasPermission(player, node);
    }

    // for commands: players need the node, the console and command blocks the operator level
    public static boolean has(CommandSourceStack source, Node node) {
        ServerPlayer player = source.getPlayer();
        return player != null ? has(player, node) : OpLevel.has(source, node.opLevel);
    }
}
