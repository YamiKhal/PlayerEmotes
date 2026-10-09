package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.client.compat.ReplayCompat;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

// all emotes playing on this client by player UUID, client thread only
public final class EmotePlayers {

    private static final Map<UUID, EmotePlayback> PLAYING = new HashMap<>();
    // remote emotes of players that are not loaded get dropped after this
    private static final int UNLOADED_GRACE_TICKS = 40;
    private static final long NO_BASE = Long.MIN_VALUE;
    // the clock: counts client ticks, in a replay follows the recorded game time (see ReplayCompat), so pausing,
    // slow motion and jumping in the replay move the emotes along
    private static int ticks;
    private static ClientLevel lastLevel;
    private static boolean replay;
    // in a replay the clock is game time minus this, kept small so float times stay exact
    private static long replayBase = NO_BASE;

    private EmotePlayers() {}

    // client time in ticks, partial tick included
    public static float time(float partialTick) {
        return ticks + partialTick;
    }

    public static boolean inReplay() {
        return replay;
    }

    // emote is null if unknown (other players' emotes)
    public static EmotePlayback start(UUID player, @Nullable ResourceLocation emote, ResourceLocation animation,
                                      EmoteNetwork.Options options, int elapsedTicks) {
        return startAt(player, emote, animation, options, ticks - elapsedTicks);
    }

    // starts an emote the server sent, started elapsedTicks before gameTime
    public static EmotePlayback startRemote(UUID player, ResourceLocation animation, EmoteNetwork.Options options,
                                            int elapsedTicks, long gameTime) {
        return startAt(player, null, animation, options, sentAt(gameTime) - elapsedTicks);
    }

    // starts one of the two players of a partner emote
    public static EmotePlayback startPartner(UUID player, ResourceLocation animation, EmoteNetwork.PartnerPlay play, PartnerLink link) {
        EmotePlayback playback = startRemote(player, animation, play.options(), play.elapsedTicks(), play.gameTime());
        playback.setLink(link, play);
        return playback;
    }

    // stops the player's emote if it belongs to the partner emote, gameTime is when the server ended it
    public static void stopPartner(UUID player, int partnership, long gameTime) {
        EmotePlayback playback = PLAYING.get(player);
        if (playback != null && playback.link() != null && playback.link().id() == partnership) {
            playback.stop(sentAt(gameTime));
        }
    }

    // stops an emote the server stopped at gameTime
    public static void stopRemote(UUID player, long gameTime) {
        EmotePlayback playback = PLAYING.get(player);
        if (playback != null) {
            playback.stop(sentAt(gameTime));
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

    // every playing emote by player, read only
    public static Map<UUID, EmotePlayback> playing() {
        return Collections.unmodifiableMap(PLAYING);
    }

    @Nullable
    public static EmotePlayback get(UUID player) {
        return PLAYING.get(player);
    }

    // whether the player has an emote that is not blending out
    public static boolean isPlaying(UUID player) {
        EmotePlayback playback = PLAYING.get(player);
        return playback != null && !playback.isStopping();
    }

    @Nullable
    public static EmotePlayback.Frame frame(UUID player, float partialTick) {
        if (PLAYING.isEmpty()) {
            return null;
        }

        EmotePlayback playback = PLAYING.get(player);
        return playback == null ? null : playback.frame(time(partialTick));
    }

    // forgets what played in the previous world and sets the replay clock, call before handling server messages
    public static void syncLevel(Minecraft minecraft) {
        boolean inReplay = ReplayCompat.inReplay();
        if (minecraft.level != lastLevel || inReplay != replay) {
            // new world or dimension (jumping back in a replay loads a new world too), nothing playing is valid anymore
            lastLevel = minecraft.level;
            replay = inReplay;
            replayBase = NO_BASE;
            PLAYING.clear();
        }

        if (replay && minecraft.level != null) {
            if (PLAYING.isEmpty()) {
                // nothing depends on the base, keep it near the current game time
                replayBase = NO_BASE;
            }

            ticks = replayTicks(minecraft.level.getGameTime());
        }
    }

    public static void tick(Minecraft minecraft) {
        syncLevel(minecraft);
        if (minecraft.isPaused()) {
            return;
        }

        if (!replay) {
            ticks++;
        }

        // server relays emotes to everyone within view distance, forget the ones of players this client never loads (or
        // no longer has), server replays them once the player comes into view
        boolean prune = ticks % 20 == 0 && minecraft.level != null;
        Iterator<Map.Entry<UUID, EmotePlayback>> iterator = PLAYING.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, EmotePlayback> entry = iterator.next();
            EmotePlayback playback = entry.getValue();
            playback.update(ticks);
            EmoteEffects.tick(minecraft, entry.getKey(), playback, ticks);
            Player player = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(entry.getKey());
            if (player != null) {
                face(player, playback);
            }

            // jumping back in a replay can leave emotes that did not start yet
            boolean unloaded = prune && ticks - playback.receivedAt() > UNLOADED_GRACE_TICKS && player == null;
            if (playback.isDone(ticks) || (replay && playback.startTime() > ticks + 1) || unloaded) {
                iterator.remove();
            }
        }
    }

    // turns the body to where the head faced when the emote started (partner emotes: their spot's facing), vanilla
    // lets the body lag behind the head so it could face a wall. runs after entities ticked, overrides vanilla's body turn
    private static void face(Player player, EmotePlayback playback) {
        if (Float.isNaN(playback.yaw)) {
            playback.yaw = player.getYHeadRot();
        }

        float yaw = playback.link() != null ? playback.link().yaw() : playback.yaw;
        player.yBodyRot += Mth.wrapDegrees(yaw - player.yBodyRot) * playback.weight(ticks);
    }

    private static EmotePlayback startAt(UUID player, @Nullable ResourceLocation emote, ResourceLocation animation,
                                         EmoteNetwork.Options options, float startTime) {
        EmotePlayback playback = new EmotePlayback(emote, animation, options, startTime, ticks);
        ClientLevel level = Minecraft.getInstance().level;
        Player entity = level == null ? null : level.getPlayerByUUID(player);
        if (entity != null) {
            playback.yaw = entity.getYHeadRot();
        }

        PLAYING.put(player, playback);
        return playback;
    }

    // clock time of a message the server sent at gameTime. live it arrived just now, in a replay it can be part of a
    // burst from jumping around, so it goes by its game time
    private static int sentAt(long gameTime) {
        return replay ? replayTicks(gameTime) : ticks;
    }

    private static int replayTicks(long gameTime) {
        if (replayBase == NO_BASE) {
            replayBase = gameTime;
        }

        return (int) (gameTime - replayBase);
    }
}
