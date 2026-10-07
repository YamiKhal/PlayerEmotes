package com.yamikhal.playeremotes.mixin.client;

//? if <1.21.2 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.animation.EmoteRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// elytra follows the torso while emoting (see EmoteRenderer#followTorso)
@Mixin(ElytraLayer.class)
public abstract class ElytraLayerMixin {

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private void playeremotes$followTorso(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntity entity,
                                          float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                          float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof AbstractClientPlayer) {
            EmoteRenderer.followTorso(poseStack, EmotePlayers.frame(entity.getUUID(), partialTick));
        }
    }
}
//?}
