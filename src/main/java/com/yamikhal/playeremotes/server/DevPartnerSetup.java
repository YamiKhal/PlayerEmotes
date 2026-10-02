package com.yamikhal.playeremotes.server;

import com.yamikhal.playeremotes.PlayerEmotes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Files;
import java.nio.file.Path;

// dev helper for the partner emote test (see the client's DevPartnerTest): if playeremotes-partnertest exists
// in the server directory, PlayerA and PlayerB are placed facing each other once both are online
final class DevPartnerSetup {

    private static Boolean enabled;
    private static boolean placed;

    private DevPartnerSetup() {}

    static void tick(MinecraftServer server) {
        if (enabled == null) {
            enabled = Files.exists(Path.of("playeremotes-partnertest"));
        }

        if (!enabled || placed || server.getTickCount() % 20 != 0) {
            return;
        }

        ServerPlayer a = server.getPlayerList().getPlayerByName("PlayerA");
        ServerPlayer b = server.getPlayerList().getPlayerByName("PlayerB");
        if (a == null || b == null || a.level() != b.level()) {
            return;
        }

        placed = true;
        double x = Math.floor(a.getX()) + 0.5;
        double y = a.getY();
        double z = Math.floor(a.getZ()) + 0.5;
        a.connection.teleport(x, y, z, 0, 0);
        b.connection.teleport(x, y, z + 0.8, 180, 0);
        PlayerEmotes.LOGGER.info("[partnertest] placed PlayerA and PlayerB facing each other");
    }
}
