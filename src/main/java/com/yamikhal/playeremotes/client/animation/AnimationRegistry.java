package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.anim.EmoteAnimation;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

// every animation inside a Blockbench file becomes <namespace>:<animation name>, files are found by EmoteRegistry
public final class AnimationRegistry {

    private static Map<ResourceLocation, EmoteAnimation> animations = Map.of();

    private AnimationRegistry() {}

    public static EmoteAnimation get(ResourceLocation id) {
        return animations.get(id);
    }

    public static int size() {
        return animations.size();
    }

    public static void set(Map<ResourceLocation, EmoteAnimation> loaded) {
        animations = Map.copyOf(loaded);
        PlayerEmotes.LOGGER.info("Loaded {} emote animations", animations.size());
    }
}
