package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.anim.EmoteAnimation;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

// every animation in a Blockbench file becomes <namespace>:<animation name>, EmoteRegistry finds the files
public final class AnimationRegistry {

    private static Map<ResourceLocation, EmoteAnimation> animations = Map.of();

    private AnimationRegistry() {}

    @Nullable
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
