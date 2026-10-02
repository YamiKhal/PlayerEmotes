package com.yamikhal.playeremotes.client.compat;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import net.minecraft.world.entity.player.Player;

import java.util.function.Function;

// Entity Model Features applies resource pack player animations after the vanilla pose, which would overwrite
// emotes, so a pause condition is registered while the player has an emote. called through reflection so there
// is no build dependency, and only when EMF is installed
public final class EmfCompat {

    public static final String MOD_ID = "entity_model_features";

    private EmfCompat() {}

    public static void init() {
        if (!PlayerEmotes.platform().isModLoaded(MOD_ID)) {
            return;
        }

        // EMF passes its EMFEntity view of the entity, which is the entity itself
        Function<Object, Boolean> emoting = entity -> entity instanceof Player player && EmotePlayers.get(player.getUUID()) != null;
        try {
            Class.forName("traben.entity_model_features.EMFAnimationApi")
                    .getMethod("registerPauseCondition", Function.class)
                    .invoke(null, emoting);
        } catch (ReflectiveOperationException | LinkageError e) {
            PlayerEmotes.LOGGER.warn("Could not register with Entity Model Features, its player animations may override emotes", e);
        }
    }
}
