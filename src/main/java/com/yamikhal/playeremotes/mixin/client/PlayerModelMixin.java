package com.yamikhal.playeremotes.mixin.client;

import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmoteRenderer;
//? if >=1.21.11 {
/*import net.minecraft.client.model.player.PlayerModel;
*///?} else
import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.9 {
/*import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
*///?} elif >=1.21.2 {
/*import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?} else {
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
//?}

// poses players once PlayerModel finished its own setup, where other mods change the player pose too. the priority
// applies emotes last: after Not Enough Animations (default 1000), whose smoothing and arm poses would drag them,
// and after PlayerAnimator / Player Animation Library (2000, 2001), which movement overhauls use for animations
// that are always active. the emote blends from whatever pose they made
@Mixin(value = PlayerModel.class, priority = 3000)
public abstract class PlayerModelMixin {

    //? if >=1.21.9 {
    /*@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("RETURN"))
    private void playeremotes$applyEmote(AvatarRenderState state, CallbackInfo ci) {
        EmotePlayback.Frame frame = ((EmoteRenderState) state).playeremotes$frame();
        if (frame != null) {
            EmoteRenderer.poseModel((PlayerModel) (Object) this, frame);
        }
    }
    *///?} elif >=1.21.2 {
    /*@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("RETURN"))
    private void playeremotes$applyEmote(PlayerRenderState state, CallbackInfo ci) {
        EmotePlayback.Frame frame = ((EmoteRenderState) state).playeremotes$frame();
        if (frame != null) {
            EmoteRenderer.poseModel((PlayerModel) (Object) this, frame);
        }
    }
    *///?} else {
    // the outer skin layers already copied the vanilla pose, so they copy the emote again
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("RETURN"))
    private void playeremotes$applyEmote(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                         float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player) || EmoteRenderer.renderingHand) {
            return;
        }

        EmotePlayback.Frame frame = EmotePlayers.frame(player.getUUID(), ageInTicks - player.tickCount);
        if (frame == null) {
            return;
        }

        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        EmoteRenderer.poseModel(model, frame);
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
    }
    //?}
}
