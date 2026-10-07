package com.yamikhal.playeremotes.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmoteProps;
import com.yamikhal.playeremotes.network.AnimatedProp;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

//? if >=1.21.9 {
/*import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
*///?} elif >=1.21.2 {
/*import com.yamikhal.playeremotes.client.animation.EmoteRenderState;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
*///?} else {
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
//?}

// emote props instead of the held items (see EmoteProps, from 1.21.2 on ArmedEntityRenderStateMixin swaps them),
// animated props drawn after the hands
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {

    //? if >=1.21.9 {
    /*@SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V", at = @At("RETURN"))
    private void playeremotes$animatedProps(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                            ArmedEntityRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (!(state instanceof EmoteRenderState emote) || emote.playeremotes$frame() == null
                || !(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model)) {
            return;
        }

        List<AnimatedProp> props = emote.playeremotes$props();
        for (int i = 0; i < props.size(); i++) {
            ItemStackRenderState item = emote.playeremotes$propItem(i);
            if (item.isEmpty()) continue;

            poseStack.pushPose();
            if (EmoteProps.place(poseStack, model, emote.playeremotes$frame(), props.get(i),
                    (arm, stack) -> ((ArmedModel) model).translateToHand(state, arm, stack))) {
                item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            }

            poseStack.popPose();
        }
    }
    *///?} elif >=1.21.2 {
    /*@Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V", at = @At("RETURN"))
    private void playeremotes$animatedProps(PoseStack poseStack, MultiBufferSource buffers, int light,
                                            ArmedEntityRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (!(state instanceof EmoteRenderState emote) || emote.playeremotes$frame() == null
                || !(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model)) {
            return;
        }

        List<AnimatedProp> props = emote.playeremotes$props();
        for (int i = 0; i < props.size(); i++) {
            ItemStackRenderState item = emote.playeremotes$propItem(i);
            if (item.isEmpty()) continue;

            poseStack.pushPose();
            if (EmoteProps.place(poseStack, model, emote.playeremotes$frame(), props.get(i), model::translateToHand)) {
                item.render(poseStack, buffers, light, OverlayTexture.NO_OVERLAY);
            }

            poseStack.popPose();
        }
    }
    *///?} else {
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
    private void playeremotes$end(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntity entity,
                                  float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                  float netHeadYaw, float headPitch, CallbackInfo ci) {
        this.playeremotes$entity = null;
        if (!(entity instanceof Player)
                || !(((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model)) {
            return;
        }

        List<AnimatedProp> props = EmoteProps.props(entity);
        EmotePlayback.Frame frame = props.isEmpty() ? null : EmotePlayers.frame(entity.getUUID(), partialTick);
        if (frame == null) {
            return;
        }

        for (int i = 0; i < props.size(); i++) {
            ItemStack item = EmoteProps.stack(entity, i);
            if (item.isEmpty()) continue;

            ItemDisplayContext context = EmoteProps.context(props.get(i), entity);
            poseStack.pushPose();
            if (EmoteProps.place(poseStack, model, frame, props.get(i), model::translateToHand)) {
                Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(entity, item, context,
                        context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND, poseStack, buffers, light);
            }

            poseStack.popPose();
        }
    }

    // first stack the method stores is the left hand's, second the right hand's
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
    //?}
}
