package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.network.EmoteProp;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// items players hold during emotes, shown in place of what their hands really hold, render thread only
public final class EmoteProps {

    private EmoteProps() {}

    // null keeps the real item
    @Nullable
    public static ItemStack propFor(LivingEntity entity, HumanoidArm arm) {
        if (!(entity instanceof Player)) {
            return null;
        }

        EmotePlayback playback = EmotePlayers.get(entity.getUUID());
        if (playback == null || playback.isStopping()) {
            return null;
        }

        EmoteProp prop = playback.options().prop();
        if (prop == null || !(arm == HumanoidArm.RIGHT ? prop.hand().right() : prop.hand().left())) {
            return null;
        }

        if (playback.propStack == null) {
            playback.propStack = BuiltInRegistries.ITEM.getOptional(prop.item()).map(ItemStack::new).orElse(ItemStack.EMPTY);
        }

        return playback.propStack;
    }
}
