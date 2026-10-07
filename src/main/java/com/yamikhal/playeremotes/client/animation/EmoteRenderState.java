package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.network.AnimatedProp;
import org.jetbrains.annotations.Nullable;

import java.util.List;

//? if >=1.21.2 {
/*import net.minecraft.client.renderer.item.ItemStackRenderState;
*///?}

// added to the player render state (1.21.2+), which carries everything from the entity to the model
public interface EmoteRenderState {

    @Nullable
    EmotePlayback.Frame playeremotes$frame();

    void playeremotes$setFrame(@Nullable EmotePlayback.Frame frame);

    // animated props of the emote, their items in playeremotes$propItem (same index)
    List<AnimatedProp> playeremotes$props();

    void playeremotes$setProps(List<AnimatedProp> props);

    //? if >=1.21.2 {
    /*ItemStackRenderState playeremotes$propItem(int index);
    *///?}
}
