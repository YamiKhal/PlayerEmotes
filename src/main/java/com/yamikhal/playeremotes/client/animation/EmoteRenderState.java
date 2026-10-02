package com.yamikhal.playeremotes.client.animation;

import org.jetbrains.annotations.Nullable;

// added to the player render state (1.21.2+), which carries everything needed to draw a player from the entity to the model
public interface EmoteRenderState {

    @Nullable
    EmotePlayback.Frame playeremotes$frame();

    void playeremotes$setFrame(@Nullable EmotePlayback.Frame frame);
}
