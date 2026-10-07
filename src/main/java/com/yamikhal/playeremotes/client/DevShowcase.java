package com.yamikhal.playeremotes.client;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.gui.Screens;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// dev helper for -Dplayeremotes.showcase=true: once in a world, third person and every emote in turn, for
// previewing emotes and as in game smoke test. with playeremotes-showcase in the game directory only emotes whose
// id contains one of its lines play, screenshotted three times each, holding a diamond sword (creative)
final class DevShowcase {

    private static final boolean ENABLED = Boolean.getBoolean("playeremotes.showcase");
    private static final int START_DELAY = 60;
    private static final int TICKS_PER_EMOTE = 50;
    private static final int[] SHOTS_AT = {12, 25, 38};

    private static int ticksInWorld;
    private static int index = -1;
    private static int nextAt;
    @Nullable
    private static List<String> filters;

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
            if (filters(minecraft) != null && minecraft.gameMode != null) {
                ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
                //? if >=1.21.5 {
                /*int slot = player.getInventory().getSelectedSlot();
                *///?} else
                int slot = player.getInventory().selected;
                player.getInventory().setItem(slot, sword);
                minecraft.gameMode.handleCreativeModeItemAdd(sword, 36 + slot);
            }
        }

        if (filters(minecraft) != null && ticksInWorld < nextAt) {
            int at = TICKS_PER_EMOTE - (nextAt - ticksInWorld);
            for (int shot : SHOTS_AT) {
                if (at == shot) {
                    shot(minecraft);
                }
            }
        }

        if (ticksInWorld < nextAt) {
            return;
        }

        List<Emote> emotes = EmoteRegistry.all();
        if (filters(minecraft) != null) {
            emotes = emotes.stream().filter(emote -> matches(minecraft, emote)).toList();
        }

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

    // emotes the filter file matches before the rest
    static List<Emote> matchingFirst(Minecraft minecraft, List<Emote> emotes) {
        if (filters(minecraft) == null) {
            return emotes;
        }

        List<Emote> sorted = new ArrayList<>(emotes);
        sorted.sort(Comparator.comparing(emote -> !matches(minecraft, emote)));
        return sorted;
    }

    private static boolean matches(Minecraft minecraft, Emote emote) {
        String id = emote.id().toString();
        return filters(minecraft).stream().anyMatch(id::contains);
    }

    // lines of the filter file, read once, null without the file or without lines
    @Nullable
    private static List<String> filters(Minecraft minecraft) {
        if (filters == null) {
            Path file = minecraft.gameDirectory.toPath().resolve("playeremotes-showcase");
            try {
                filters = !Files.exists(file) ? List.of() : Files.readAllLines(file).stream()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .toList();
            } catch (IOException e) {
                filters = List.of();
            }
        }

        return filters.isEmpty() ? null : filters;
    }

    private static void shot(Minecraft minecraft) {
        //? if >=26.3 {
        /*Screenshot.grab(minecraft, false);
        *///?} else
        Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), message -> {});
    }
}
