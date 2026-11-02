package com.yamikhal.playeremotes.client.compat;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

// Entity Model Features applies resource pack player animations after the vanilla pose and would overwrite
// emotes, so a pause condition while the player emotes. through reflection, no build dependency, only when EMF
// is installed
public final class EmfCompat {

    public static final String MOD_ID = "entity_model_features";

    // previews always pause, EMF would animate them with its idle animations
    private static boolean previewing;
    // ETF render state plumbing, null without EMF or when ETF changed it
    @Nullable
    private static Method initState;
    @Nullable
    private static Method forEntity;
    @Nullable
    private static Method mount;
    @Nullable
    private static Method unMount;
    private static boolean mounted;
    // EMF's paused entities. emf_compat addons lift pause conditions while they hold a pose (NEA torch arm), not
    // this set, so emoting players go in here too
    @Nullable
    private static Set<UUID> pausedEntities;
    private static final Set<UUID> PAUSED_BY_US = new HashSet<>();
    @Nullable
    private static UUID previewPaused;

    private EmfCompat() {}

    @SuppressWarnings("unchecked")
    public static void init() {
        if (!PlayerEmotes.platform().isModLoaded(MOD_ID)) {
            return;
        }

        // EMF passes its EMFEntity view of the entity, which is the entity itself
        Function<Object, Boolean> paused = entity -> previewing
                || entity instanceof Player player && EmotePlayers.get(player.getUUID()) != null;
        try {
            Class.forName("traben.entity_model_features.EMFAnimationApi")
                    .getMethod("registerPauseCondition", Function.class)
                    .invoke(null, paused);
        } catch (ReflectiveOperationException | LinkageError e) {
            PlayerEmotes.LOGGER.warn("Could not register with Entity Model Features, its player animations may override emotes", e);
            return;
        }

        try {
            pausedEntities = (Set<UUID>) Class.forName("traben.entity_model_features.utils.EMFAnimationPauseHandler")
                    .getField("entitiesPaused").get(null);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            PlayerEmotes.LOGGER.warn("Could not find Entity Model Features' paused entities, addons may still override emotes", e);
        }

        // EMF only asks the pause condition with an entity, previews borrow the local player's ETF state
        try {
            Class<?> etfEntity = Class.forName("traben.entity_texture_features.utils.ETFEntity");
            Class<?> etfRenderState = Class.forName("traben.entity_texture_features.features.state.ETFEntityRenderState");
            Class<?> etfState = Class.forName("traben.entity_texture_features.features.state.ETFState");
            //? if >=1.21.9
            /*initState = Class.forName("traben.entity_texture_features.features.state.HoldsETFRenderState").getMethod("etf$initState", etfEntity);*/
            forEntity = etfRenderState.getMethod("forEntity", etfEntity);
            mount = etfState.getMethod("mount", etfRenderState);
            unMount = etfState.getMethod("unMount");
        } catch (ReflectiveOperationException | LinkageError e) {
            PlayerEmotes.LOGGER.warn("Could not hook into Entity Texture Features, emote previews may show resource pack animations", e);
            disable();
        }
    }

    // gives a preview render state the player's ETF state, like extracting it from the player would (1.21.9+)
    public static void attachPlayer(Object renderState, @Nullable Player player) {
        if (initState == null || player == null) {
            return;
        }

        try {
            initState.invoke(renderState, player);
        } catch (ReflectiveOperationException | LinkageError e) {
            fail(e);
        }
    }

    // keeps emoting players in EMF's paused entities, removes only the ones added here
    public static void tick() {
        if (pausedEntities == null) {
            return;
        }

        Set<UUID> emoting = EmotePlayers.playing().keySet();
        for (Iterator<UUID> it = PAUSED_BY_US.iterator(); it.hasNext(); ) {
            UUID uuid = it.next();
            if (!emoting.contains(uuid)) {
                pausedEntities.remove(uuid);
                it.remove();
            }
        }

        for (UUID uuid : emoting) {
            if (pausedEntities.add(uuid)) {
                PAUSED_BY_US.add(uuid);
            }
        }
    }

    // pauses EMF while a preview of the player draws. previews drawn without an entity renderer (before 1.21.9)
    // mount the player's ETF state, before posing since mounting resets EMF's player parts
    public static void beginPreview(@Nullable Player player) {
        previewing = true;
        if (player == null) {
            return;
        }

        if (pausedEntities != null && pausedEntities.add(player.getUUID())) {
            previewPaused = player.getUUID();
        }

        if (initState != null || forEntity == null || mount == null) {
            return;
        }

        try {
            mount.invoke(null, forEntity.invoke(null, player));
            mounted = true;
        } catch (ReflectiveOperationException | LinkageError e) {
            fail(e);
        }
    }

    public static void endPreview() {
        previewing = false;
        if (previewPaused != null && pausedEntities != null) {
            pausedEntities.remove(previewPaused);
            previewPaused = null;
        }

        if (!mounted || unMount == null) {
            return;
        }

        mounted = false;
        try {
            unMount.invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            fail(e);
        }
    }

    private static void fail(Throwable e) {
        PlayerEmotes.LOGGER.warn("Entity Texture Features call failed, emote previews may show resource pack animations", e);
        disable();
    }

    private static void disable() {
        initState = null;
        forEntity = null;
        mount = null;
        unMount = null;
    }
}
