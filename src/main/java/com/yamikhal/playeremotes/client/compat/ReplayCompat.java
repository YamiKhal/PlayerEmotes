package com.yamikhal.playeremotes.client.compat;

import com.yamikhal.playeremotes.PlayerEmotes;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

// Replay Mod and Flashback record what the server sends and play it back later. in a replay the world is no
// real server: emotes follow the recorded game time (see EmotePlayers) and the local player cannot emote or talk
// to the server. both asked through reflection, no build dependency, only when installed
public final class ReplayCompat {

    public static final String REPLAY_MOD_ID = "replaymod";
    public static final String FLASHBACK_ID = "flashback";

    // Flashback.isInReplay()
    @Nullable
    private static Method flashbackInReplay;
    // ReplayModReplay.instance and its getReplayHandler(), non-null while a replay is open
    @Nullable
    private static Field replayModInstance;
    @Nullable
    private static Method replayModHandler;

    private ReplayCompat() {}

    public static void init() {
        if (PlayerEmotes.platform().isModLoaded(FLASHBACK_ID)) {
            try {
                flashbackInReplay = Class.forName("com.moulberry.flashback.Flashback").getMethod("isInReplay");
            } catch (ReflectiveOperationException | LinkageError e) {
                PlayerEmotes.LOGGER.warn("Could not hook into Flashback, emotes may play wrong in its replays", e);
            }
        }

        if (PlayerEmotes.platform().isModLoaded(REPLAY_MOD_ID)) {
            try {
                Class<?> replay = Class.forName("com.replaymod.replay.ReplayModReplay");
                replayModInstance = replay.getField("instance");
                replayModHandler = replay.getMethod("getReplayHandler");
            } catch (ReflectiveOperationException | LinkageError e) {
                PlayerEmotes.LOGGER.warn("Could not hook into Replay Mod, emotes may play wrong in its replays", e);
            }
        }
    }

    // whether the shown world is a replay, client thread only
    public static boolean inReplay() {
        return (flashbackInReplay != null && flashback()) || (replayModHandler != null && replayMod());
    }

    private static boolean flashback() {
        try {
            return (boolean) flashbackInReplay.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            PlayerEmotes.LOGGER.warn("Lost the hook into Flashback", e);
            flashbackInReplay = null;
            return false;
        }
    }

    private static boolean replayMod() {
        try {
            Object instance = replayModInstance.get(null);
            return instance != null && replayModHandler.invoke(instance) != null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            PlayerEmotes.LOGGER.warn("Lost the hook into Replay Mod", e);
            replayModHandler = null;
            return false;
        }
    }
}
