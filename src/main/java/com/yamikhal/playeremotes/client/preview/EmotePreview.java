package com.yamikhal.playeremotes.client.preview;

import com.yamikhal.playeremotes.anim.EmoteAnimation;
import com.yamikhal.playeremotes.client.animation.AnimationRegistry;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.emote.Emote;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// timing of the 3D previews in the menus: frozen pose until active (hovered or selected), then plays from the
// start, resets once no longer active
public final class EmotePreview {

    // pause after a non looping animation before it starts over
    private static final double RESTART_DELAY = 0.75;
    // start time in milliseconds per active preview
    private static final Map<Object, Long> ACTIVE = new HashMap<>();

    private EmotePreview() {}

    // forgets all running previews, they start over next time a screen shows them
    public static void reset() {
        ACTIVE.clear();
    }

    // key identifies the preview (e.g. a wheel slot) so it keeps its own timer, null if the emote has no loaded
    // animation
    @Nullable
    public static EmotePlayback.Frame frame(Emote emote, Object key, boolean active) {
        EmoteAnimation animation = animation(emote);
        if (animation == null) {
            return null;
        }

        if (!active) {
            ACTIVE.remove(key);
            return frame(emote, animation, previewTime(emote, animation));
        }

        long now = System.nanoTime() / 1_000_000;
        double seconds = (now - ACTIVE.computeIfAbsent(key, k -> now)) / 1000.0;
        if (animation.loop() != EmoteAnimation.LoopMode.LOOP && seconds > animation.length() + RESTART_DELAY) {
            ACTIVE.put(key, now);
            seconds = 0;
        }

        return frame(emote, animation, seconds);
    }

    private static EmotePlayback.Frame frame(Emote emote, EmoteAnimation animation, double seconds) {
        return new EmotePlayback.Frame(animation, seconds, 1, emote.look(), emote.splitLimbs(), null, emote.prop(), emote.props());
    }

    @Nullable
    private static EmoteAnimation animation(Emote emote) {
        // partner emotes show what the starter does once someone joined
        if (emote.partner() != null) {
            EmoteAnimation action = AnimationRegistry.get(emote.partner().action());
            if (action != null) {
                return action;
            }
        }

        for (ResourceLocation id : emote.animations()) {
            EmoteAnimation animation = AnimationRegistry.get(id);
            if (animation != null) {
                return animation;
            }
        }

        return null;
    }

    private static double previewTime(Emote emote, EmoteAnimation animation) {
        return emote.previewSeconds() >= 0 ? emote.previewSeconds() : animation.length() * 0.5;
    }
}
