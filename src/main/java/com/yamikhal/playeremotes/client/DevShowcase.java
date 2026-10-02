package com.yamikhal.playeremotes.client;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.gui.Screens;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;

// dev helper enabled with -Dplayeremotes.showcase=true: once in a world it switches to third person and plays
// every emote in turn, handy for previewing emotes and as an in-game smoke test
final class DevShowcase {

    private static final boolean ENABLED = Boolean.getBoolean("playeremotes.showcase");
    private static final int START_DELAY = 60;
    private static final int TICKS_PER_EMOTE = 50;

    private static int ticksInWorld;
    private static int index = -1;
    private static int nextAt;

    private DevShowcase() {}

    static void tick(Minecraft minecraft) {
        if (!ENABLED || index == Integer.MAX_VALUE || DevUiShots.tick(minecraft)) {
            return;
        }

        LocalPlayer player = minecraft.player;
        if (player == null || Screens.current() != null) {
            return;
        }

        ticksInWorld++;
        if (ticksInWorld < START_DELAY) {
            return;
        }

        if (index < 0) {
            minecraft.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            index = 0;
            nextAt = ticksInWorld;
        }

        if (ticksInWorld < nextAt) {
            return;
        }

        List<Emote> emotes = EmoteRegistry.all();
        if (index >= emotes.size()) {
            PlayerEmotesClient.stop();
            PlayerEmotes.LOGGER.info("[showcase] complete, played {} emotes", emotes.size());
            index = Integer.MAX_VALUE;
            return;
        }

        Emote emote = emotes.get(index++);
        PlayerEmotes.LOGGER.info("[showcase] {} ({}/{})", emote.id(), index, emotes.size());
        PlayerEmotesClient.play(emote);
        if (!EmotePlayers.isPlaying(player.getUUID())) {
            PlayerEmotes.LOGGER.warn("[showcase] {} did not start", emote.id());
        }

        nextAt = ticksInWorld + TICKS_PER_EMOTE;
    }
}
