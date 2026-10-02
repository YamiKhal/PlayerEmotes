package com.yamikhal.playeremotes.mixin.client;

import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmoteRenderer;
import net.minecraft.client.model.HumanoidModel;
//? if >=1.21.11 {
/*import net.minecraft.client.model.player.PlayerModel;
*///?} else
import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.2 {
/*import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
*///?} else {
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
//?}

// poses armor and other humanoid models drawn for an emoting player, the player model itself is posed by PlayerModelMixin
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    //? if >=1.21.2 {
    /*@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void playeremotes$applyEmote(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof EmoteRenderState emoteState) || (Object) this instanceof PlayerModel) {
            return;
        }

        EmotePlayback.Frame frame = emoteState.playeremotes$frame();
        if (frame != null) {
            EmoteRenderer.poseModel((HumanoidModel<?>) (Object) this, frame);
        }
    }
    *///?} else {
    @Shadow
    @Final
    public ModelPart head;
    @Shadow
    @Final
    public ModelPart hat;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("HEAD"))
    private void playeremotes$resetPose(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                        float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player) {
            EmoteRenderer.resetPose((HumanoidModel<?>) (Object) this);
        }
    }

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void playeremotes$applyEmote(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                         float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!(entity instanceof AbstractClientPlayer player) || EmoteRenderer.renderingHand || (Object) this instanceof PlayerModel) {
            return;
        }

        EmotePlayback.Frame frame = EmotePlayers.frame(player.getUUID(), ageInTicks - player.tickCount);
        if (frame == null) {
            return;
        }

        EmoteRenderer.poseModel((HumanoidModel<?>) (Object) this, frame);
        this.hat.copyFrom(this.head);
    }
    //?}
}
