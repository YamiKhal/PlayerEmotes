package com.yamikhal.playeremotes.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.animation.EmoteRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=1.21.9 {
/*import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
*///?} elif >=1.21.2 {
/*import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
*///?} else {
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
//?}

// applies the whole-body transform and hands the current emote frame to the model
//? if >=1.21.9 {
/*@Mixin(AvatarRenderer.class)
*///?} else
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {

    //? if >=1.21.9 {
    /*@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V", at = @At("TAIL"))
    private void playeremotes$captureFrame(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ((EmoteRenderState) state).playeremotes$setFrame(EmotePlayers.frame(avatar.getUUID(), partialTick));
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void playeremotes$poseBody(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        EmotePlayback.Frame frame = ((EmoteRenderState) state).playeremotes$frame();
        if (frame == null) {
            return;
        }

        EmoteRenderer.alignPartner(poseStack, frame, state.x, state.z, bodyRot);
        EmoteRenderer.poseBody(poseStack, frame);
    }
    *///?} elif >=1.21.2 {
    /*@Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V", at = @At("TAIL"))
    private void playeremotes$captureFrame(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        ((EmoteRenderState) state).playeremotes$setFrame(EmotePlayers.frame(player.getUUID(), partialTick));
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V", at = @At("TAIL"))
    private void playeremotes$poseBody(PlayerRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        EmotePlayback.Frame frame = ((EmoteRenderState) state).playeremotes$frame();
        if (frame == null) {
            return;
        }

        EmoteRenderer.alignPartner(poseStack, frame, state.x, state.z, bodyRot);
        EmoteRenderer.poseBody(poseStack, frame);
    }
    *///?} else {
    //? if >=1.20.5 {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V", at = @At("TAIL"))
    private void playeremotes$poseBody(AbstractClientPlayer player, PoseStack poseStack, float bob, float yBodyRot,
                                       float partialTick, float scale, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("TAIL"))
    private void playeremotes$poseBody(AbstractClientPlayer player, PoseStack poseStack, float bob, float yBodyRot,
                                       float partialTick, CallbackInfo ci) {
    *///?}
        EmotePlayback.Frame frame = EmotePlayers.frame(player.getUUID(), partialTick);
        if (frame == null) {
            return;
        }

        EmoteRenderer.alignPartner(poseStack, frame, net.minecraft.util.Mth.lerp(partialTick, player.xo, player.getX()),
                net.minecraft.util.Mth.lerp(partialTick, player.zo, player.getZ()), yBodyRot);
        EmoteRenderer.poseBody(poseStack, frame);
    }

    // the first person hand runs setupAnim too, it must not be animated
    @Inject(method = "renderHand", at = @At("HEAD"))
    private void playeremotes$beginHand(CallbackInfo ci) {
        EmoteRenderer.renderingHand = true;
    }

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void playeremotes$endHand(CallbackInfo ci) {
        EmoteRenderer.renderingHand = false;
    }
    //?}
}
