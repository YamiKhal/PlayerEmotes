package com.yamikhal.playeremotes.server;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

//? if >=1.21.11 {
/*import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.Permissions;
*///?}

// vanilla operator levels, permission sets since 1.21.11
public final class OpLevel {

    private OpLevel() {}

    public static boolean has(@Nullable ServerPlayer player, int level) {
        if (level <= 0) {
            return true;
        }

        if (player == null) {
            return false;
        }

        //? if >=1.21.11 {
        /*return player.permissions().hasPermission(permission(level));
        *///?} else
        return player.hasPermissions(level);
    }

    public static boolean has(CommandSourceStack source, int level) {
        if (level <= 0) {
            return true;
        }

        //? if >=1.21.11 {
        /*return source.permissions().hasPermission(permission(level));
        *///?} else
        return source.hasPermission(level);
    }

    //? if >=1.21.11 {
    /*private static Permission permission(int level) {
        return switch (level) {
            case 1 -> Permissions.COMMANDS_MODERATOR;
            case 2 -> Permissions.COMMANDS_GAMEMASTER;
            case 3 -> Permissions.COMMANDS_ADMIN;
            default -> Permissions.COMMANDS_OWNER;
        };
    }
    *///?}
}
