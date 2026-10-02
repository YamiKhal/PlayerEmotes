package com.yamikhal.playeremotes.mixin.client;

//? if <1.21.2 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.yamikhal.playeremotes.client.animation.EmoteProps;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// emote props in place of the held items (see EmoteProps)
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    @Unique
    private static final String RENDER = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V";
    @Unique
    private LivingEntity playeremotes$entity;

    @Inject(method = RENDER, at = @At("HEAD"))
    private void playeremotes$begin(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntity entity,
                                    float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                    float netHeadYaw, float headPitch, CallbackInfo ci) {
        this.playeremotes$entity = entity;
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void playeremotes$end(CallbackInfo ci) {
        this.playeremotes$entity = null;
    }

    // the first stack the method stores is the left hand's, the second the right hand's
    @ModifyVariable(method = RENDER, at = @At("STORE"), ordinal = 0)
    private ItemStack playeremotes$leftHand(ItemStack stack) {
        ItemStack prop = this.playeremotes$entity == null ? null : EmoteProps.propFor(this.playeremotes$entity, HumanoidArm.LEFT);
        return prop != null ? prop : stack;
    }

    @ModifyVariable(method = RENDER, at = @At("STORE"), ordinal = 1)
    private ItemStack playeremotes$rightHand(ItemStack stack) {
        ItemStack prop = this.playeremotes$entity == null ? null : EmoteProps.propFor(this.playeremotes$entity, HumanoidArm.RIGHT);
        return prop != null ? prop : stack;
    }
}
//?}
