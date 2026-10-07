package com.yamikhal.playeremotes.mixin.client;

//? if >=1.21.2 {
/*import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import com.yamikhal.playeremotes.network.AnimatedProp;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
//? if >=1.21.9 {
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
//?} else
/^import net.minecraft.client.renderer.entity.state.PlayerRenderState;^/

import java.util.List;

//? if >=1.21.9 {
@Mixin(AvatarRenderState.class)
//?} else
/^@Mixin(PlayerRenderState.class)^/
public abstract class PlayerRenderStateMixin implements EmoteRenderState {

    @Unique
    @Nullable
    private EmotePlayback.Frame playeremotes$frame;
    @Unique
    private List<AnimatedProp> playeremotes$props = List.of();
    @Unique
    private final ItemStackRenderState[] playeremotes$propItems = new ItemStackRenderState[AnimatedProp.MAX_PROPS];

    @Override
    public EmotePlayback.Frame playeremotes$frame() {
        return this.playeremotes$frame;
    }

    @Override
    public void playeremotes$setFrame(@Nullable EmotePlayback.Frame frame) {
        this.playeremotes$frame = frame;
    }

    @Override
    public List<AnimatedProp> playeremotes$props() {
        return this.playeremotes$props;
    }

    @Override
    public void playeremotes$setProps(List<AnimatedProp> props) {
        this.playeremotes$props = props;
    }

    // made on first use, render states are reused between frames
    @Override
    public ItemStackRenderState playeremotes$propItem(int index) {
        if (this.playeremotes$propItems[index] == null) {
            this.playeremotes$propItems[index] = new ItemStackRenderState();
        }

        return this.playeremotes$propItems[index];
    }
}
*///?}
