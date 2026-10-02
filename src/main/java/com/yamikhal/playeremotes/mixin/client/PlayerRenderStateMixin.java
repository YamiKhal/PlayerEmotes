package com.yamikhal.playeremotes.mixin.client;

//? if >=1.21.2 {
/*import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//? if >=1.21.9 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} else
/^import net.minecraft.client.renderer.entity.state.PlayerRenderState;^/

//? if >=1.21.9 {
@Mixin(AvatarRenderState.class)
//?} else
/^@Mixin(PlayerRenderState.class)^/
public abstract class PlayerRenderStateMixin implements EmoteRenderState {

    @Unique
    @Nullable
    private EmotePlayback.Frame playeremotes$frame;

    @Override
    public EmotePlayback.Frame playeremotes$frame() {
        return this.playeremotes$frame;
    }

    @Override
    public void playeremotes$setFrame(@Nullable EmotePlayback.Frame frame) {
        this.playeremotes$frame = frame;
    }
}
*///?}
