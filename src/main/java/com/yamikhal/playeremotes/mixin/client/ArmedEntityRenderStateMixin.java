package com.yamikhal.playeremotes.mixin.client;

//? if >=1.21.2 {
/*import com.yamikhal.playeremotes.client.animation.EmoteProps;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// emote props in place of the held items (see EmoteProps)
@Mixin(ArmedEntityRenderState.class)
public abstract class ArmedEntityRenderStateMixin {

    //? if >=1.21.11 {
    @Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
    private static void playeremotes$props(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver resolver,
                                           float partialTick, CallbackInfo ci) {
        ItemStack right = EmoteProps.propFor(entity, HumanoidArm.RIGHT);
        if (right != null) {
            resolver.updateForLiving(state.rightHandItemState, right, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, entity);
            state.rightHandItemStack = right.copy();
        }

        ItemStack left = EmoteProps.propFor(entity, HumanoidArm.LEFT);
        if (left != null) {
            resolver.updateForLiving(state.leftHandItemState, left, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, entity);
            state.leftHandItemStack = left.copy();
        }
    }
    //?} else {
    /^@Inject(method = "extractArmedEntityRenderState", at = @At("TAIL"))
    private static void playeremotes$props(LivingEntity entity, ArmedEntityRenderState state, ItemModelResolver resolver,
                                           CallbackInfo ci) {
        ItemStack right = EmoteProps.propFor(entity, HumanoidArm.RIGHT);
        if (right != null) {
            resolver.updateForLiving(state.rightHandItem, right, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, entity);
        }

        ItemStack left = EmoteProps.propFor(entity, HumanoidArm.LEFT);
        if (left != null) {
            resolver.updateForLiving(state.leftHandItem, left, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, entity);
        }
    }
    ^///?}
}
*///?}
