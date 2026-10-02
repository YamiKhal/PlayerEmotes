package com.yamikhal.playeremotes.mixin.client;

//? if >=1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import com.yamikhal.playeremotes.client.animation.EmoteRenderer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else
/^import net.minecraft.client.renderer.MultiBufferSource;^/

// the elytra follows the torso while emoting (see EmoteRenderer#followTorso)
@Mixin(WingsLayer.class)
public abstract class WingsLayerMixin {

    //? if >=1.21.9 {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void playeremotes$followTorso(PoseStack poseStack, SubmitNodeCollector collector, int light, HumanoidRenderState state,
                                          float yRot, float xRot, CallbackInfo ci) {
    //?} else {
    /^@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void playeremotes$followTorso(PoseStack poseStack, MultiBufferSource buffers, int light, HumanoidRenderState state,
                                          float yRot, float xRot, CallbackInfo ci) {
    ^///?}
        if (state instanceof EmoteRenderState emoteState) {
            EmoteRenderer.followTorso(poseStack, emoteState.playeremotes$frame());
        }
    }
}
*///?}
