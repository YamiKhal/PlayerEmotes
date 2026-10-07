package com.yamikhal.playeremotes.platform;

import com.yamikhal.playeremotes.server.EmotePermissions;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

// the few things that differ per mod loader, one implementation per loader
public interface Platform {

    boolean isModLoaded(String modId);

    Path configDir();

    // sends an encoded EmoteNetwork message to a player
    void sendToPlayer(ServerPlayer player, byte[] message);

    // checks a permission through the loader's permission API, falls back to the node's operator level
    boolean hasPermission(ServerPlayer player, EmotePermissions.Node node);
}
