package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.anim.EmoteAnimation;
import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

// plays sound and particle keyframes of running emotes, each client on its own from the animation clock so
// effects cost no network traffic, client thread only
final class EmoteEffects {

    // effects of an emote older than this (replay for a late viewer) are not caught up on
    private static final double CATCH_UP_SECONDS = 0.25;
    // most loop periods caught up on in one tick, in case a tick ever covers several
    private static final int MAX_CYCLES = 2;

    private static final Map<String, Optional<ParticleOptions>> PARTICLES = new HashMap<>();
    private static final Map<String, Optional<SoundEvent>> SOUNDS = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private EmoteEffects() {}

    static void tick(Minecraft minecraft, UUID playerId, EmotePlayback playback, float now) {
        if (playback.isStopping() || minecraft.level == null) {
            return;
        }

        EmoteAnimation animation = AnimationRegistry.get(playback.animationId());
        if (animation == null || animation.effects().isEmpty()) {
            return;
        }

        double elapsed = (now - playback.startTime()) / 20.0;
        double previous = playback.effectTime;
        playback.effectTime = elapsed;
        if (Double.isNaN(previous)) {
            // first tick: fresh emotes fire keyframes from 0, replays start where they are
            if (elapsed > CATCH_UP_SECONDS) {
                return;
            }

            previous = -1e-9;
        }

        // jump in a replay skips the effects on the way, like a late viewer
        if (elapsed <= previous || elapsed - previous > CATCH_UP_SECONDS) {
            return;
        }

        Player player = minecraft.level.getPlayerByUUID(playerId);
        if (player == null) {
            return;
        }

        boolean loops = animation.loop() == EmoteAnimation.LoopMode.LOOP && animation.length() > 0;
        long firstCycle = loops ? (long) Math.floor(Math.max(0, previous) / animation.length()) : 0;
        long lastCycle = loops ? Math.min(firstCycle + MAX_CYCLES, (long) Math.floor(elapsed / animation.length())) : 0;
        for (long cycle = firstCycle; cycle <= lastCycle; cycle++) {
            double offset = cycle * animation.length();
            for (EmoteAnimation.Effect effect : animation.effects()) {
                double time = offset + effect.time();
                if (time > previous && time <= elapsed) {
                    fire(minecraft.level, player, playback, effect);
                }
            }
        }
    }

    private static void fire(ClientLevel level, Player player, EmotePlayback playback, EmoteAnimation.Effect effect) {
        EmotePlayback.Frame frame = playback.frame(EmotePlayers.time(0));
        if (frame == null) {
            return;
        }

        Vec3 position = EmoteRenderer.locatorPosition(player, frame, effect.locator());
        if (effect.kind() == EmoteAnimation.Effect.Kind.SOUND) {
            boolean own = player == Minecraft.getInstance().player;
            if (own ? !PlayerEmotesClient.config().playEmoteSounds : !PlayerEmotesClient.config().hearOtherSounds) {
                return;
            }

            SOUNDS.computeIfAbsent(effect.effect(), EmoteEffects::sound).ifPresent(sound ->
                    level.playLocalSound(position.x, position.y, position.z, sound, SoundSource.PLAYERS,
                            effect.volume(), effect.pitch(), false));
        } else {
            PARTICLES.computeIfAbsent(effect.effect(), EmoteEffects::particle).ifPresent(particle ->
                    level.addParticle(particle, position.x, position.y, position.z, 0, 0, 0));
        }
    }

    private static Optional<SoundEvent> sound(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null) {
            warn("Invalid sound effect id '{}'", name);
            return Optional.empty();
        }

        // sounds only need a sounds.json entry, not the registry
        return Optional.of(SoundEvent.createVariableRangeEvent(id));
    }

    // only particles without options (hearts, notes, smoke, ...) work in a keyframe
    private static Optional<ParticleOptions> particle(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        Object type = id == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null);
        if (type instanceof ParticleOptions options) {
            return Optional.of(options);
        }

        warn(type == null ? "Unknown particle effect '{}'" : "Particle effect '{}' needs options and cannot be used", name);
        return Optional.empty();
    }

    private static void warn(String message, String name) {
        if (WARNED.add(name)) {
            PlayerEmotes.LOGGER.warn(message, name);
        }
    }
}
