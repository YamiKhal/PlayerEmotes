package com.yamikhal.playeremotes.client;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.config.EmoteConfig;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.gui.EmoteListScreen;
import com.yamikhal.playeremotes.client.gui.QuickWheelScreen;
import com.yamikhal.playeremotes.client.gui.Screens;
import com.yamikhal.playeremotes.client.gui.SettingsScreen;
import com.yamikhal.playeremotes.client.gui.WheelEditScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.util.List;

// dev helper for the showcase run: if playeremotes-uishots exists in the game directory, it opens the emote screens
// once in a world and saves a screenshot of each (to screenshots/) before the showcase starts, only the game's own
// frame is captured
final class DevUiShots {

    private static final int START_DELAY = 60;
    private static final int STEP = 40;

    private static int ticks = -1;

    private DevUiShots() {}

    // returns whether it is still busy, which holds back the showcase
    static boolean tick(Minecraft minecraft) {
        if (ticks == Integer.MAX_VALUE) {
            return false;
        }

        if (ticks < 0) {
            if (minecraft.player == null) {
                return true;
            }

            if (!new File(minecraft.gameDirectory, "playeremotes-uishots").exists()) {
                ticks = Integer.MAX_VALUE;
                return false;
            }

            ticks = 0;
        }

        ticks++;
        List<Emote> emotes = EmoteRegistry.all();
        if (ticks == START_DELAY) {
            EmoteConfig config = PlayerEmotesClient.config();
            for (int slot = 0; slot < EmoteConfig.SLOTS && slot < emotes.size(); slot++) {
                config.setWheelSlot(0, slot, emotes.get(slot).id());
            }

            // a running emote, so the wheel shows its stop link
            Emote looping = EmoteRegistry.get(PlayerEmotes.id("robot_dance"));
            if (looping != null) {
                PlayerEmotesClient.play(looping);
            }

            Screens.open(new QuickWheelScreen(null));
        } else if (ticks == START_DELAY + STEP) {
            shot(minecraft);
            EmoteListScreen list = new EmoteListScreen(null);
            Screens.open(list);
            if (emotes.size() > 1) {
                list.select(emotes.get(1));
            }
        } else if (ticks == START_DELAY + 2 * STEP) {
            shot(minecraft);
            Screens.open(new WheelEditScreen(null));
        } else if (ticks == START_DELAY + 3 * STEP) {
            shot(minecraft);
            Screens.open(new SettingsScreen(null));
        } else if (ticks == START_DELAY + 4 * STEP) {
            shot(minecraft);
            Screens.open(null);
            PlayerEmotes.LOGGER.info("[uishots] done");
            ticks = Integer.MAX_VALUE;
            return false;
        }

        return true;
    }

    private static void shot(Minecraft minecraft) {
        //? if >=26.3 {
        /*Screenshot.grab(minecraft, false);
        *///?} else
        Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), message -> PlayerEmotes.LOGGER.info("[uishots] {}", message.getString()));
    }
}
