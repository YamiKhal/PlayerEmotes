package com.yamikhal.playeremotes.mixin.client;

//? if <1.21.2 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.animation.EmoteRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the cape follows the torso while emoting (see EmoteRenderer#followTorso)
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void playeremotes$followTorso(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                                          float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                          float netHeadYaw, float headPitch, CallbackInfo ci) {
        EmoteRenderer.followTorso(poseStack, EmotePlayers.frame(player.getUUID(), partialTick));
    }
}
//?}
