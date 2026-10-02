package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.network.EmoteNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// all emotes currently playing on this client keyed by player UUID, client thread only
public final class EmotePlayers {

    private static final Map<UUID, EmotePlayback> PLAYING = new HashMap<>();
    // remote emotes of players that are not loaded are dropped after this long
    private static final int UNLOADED_GRACE_TICKS = 40;
    private static int ticks;
    private static ClientLevel lastLevel;

    private EmotePlayers() {}

    // client time in ticks, including the partial tick
    public static float time(float partialTick) {
        return ticks + partialTick;
    }

    // emote is null if unknown (other players' emotes)
    public static EmotePlayback start(UUID player, @Nullable ResourceLocation emote, ResourceLocation animation,
                                      EmoteNetwork.Options options, int elapsedTicks) {
        EmotePlayback playback = new EmotePlayback(emote, animation, options, ticks - elapsedTicks, ticks);
        PLAYING.put(player, playback);
        return playback;
    }

    // starts one of the two players of a partner emote
    public static EmotePlayback startPartner(UUID player, ResourceLocation animation, EmoteNetwork.Options options,
                                             int elapsedTicks, PartnerLink link) {
        EmotePlayback playback = start(player, null, animation, options, elapsedTicks);
        playback.setLink(link);
        return playback;
    }

    // stops the player's emote if it belongs to the given partner emote
    public static void stopPartner(UUID player, int partnership) {
        EmotePlayback playback = PLAYING.get(player);
        if (playback != null && playback.link() != null && playback.link().id() == partnership) {
            playback.stop(ticks);
        }
    }

    public static void stop(UUID player) {
        EmotePlayback playback = PLAYING.get(player);
        if (playback != null) {
            playback.stop(ticks);
        }
    }

    // blends out every emote except the given player's
    public static void stopAllExcept(UUID player) {
        PLAYING.forEach((id, playback) -> {
            if (!id.equals(player)) {
                playback.stop(ticks);
            }
        });
    }

    public static EmotePlayback get(UUID player) {
        return PLAYING.get(player);
    }

    // whether the player has an emote that is not blending out
    public static boolean isPlaying(UUID player) {
        EmotePlayback playback = PLAYING.get(player);
        return playback != null && !playback.isStopping();
    }

    public static EmotePlayback.Frame frame(UUID player, float partialTick) {
        if (PLAYING.isEmpty()) {
            return null;
        }

        EmotePlayback playback = PLAYING.get(player);
        return playback == null ? null : playback.frame(time(partialTick));
    }

    public static void tick(Minecraft minecraft) {
        if (minecraft.level != lastLevel) {
            // new world or dimension: nothing that was playing is valid anymore
            lastLevel = minecraft.level;
            PLAYING.clear();
        }

        if (minecraft.isPaused()) {
            return;
        }

        ticks++;

        // the server relays emotes to the whole dimension, forget those of players this client never loads (or no
        // longer has loaded), the server replays them once the player comes into view
        boolean prune = ticks % 20 == 0 && minecraft.level != null;
        Iterator<Map.Entry<UUID, EmotePlayback>> iterator = PLAYING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, EmotePlayback> entry = iterator.next();
            EmotePlayback playback = entry.getValue();
            playback.update(ticks);
            EmoteEffects.tick(minecraft, entry.getKey(), playback, ticks);
            if (playback.isDone(ticks) || (prune && ticks - playback.receivedAt() > UNLOADED_GRACE_TICKS
                    && minecraft.level.getPlayerByUUID(entry.getKey()) == null)) {
                iterator.remove();
            }
        }
    }
}
