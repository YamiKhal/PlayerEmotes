package com.yamikhal.playeremotes.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.yamikhal.playeremotes.anim.Bend;
import com.yamikhal.playeremotes.client.animation.BendablePart;
import com.yamikhal.playeremotes.client.animation.EmoteRenderer;
import com.yamikhal.playeremotes.client.animation.LimbMesh;
import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// draws bent limbs (see Bend). only the cubes of a bent part get replaced, its transform, children and vertex
// consumer stay vanilla's, so render types, shaders and layers drawing the part again keep working. mods drawing
// cubes their own way hook the cubes (Sodium), which a bent part skips. low priority runs this before mods that
// would cancel the whole part's cubes at the same spot
@Mixin(value = ModelPart.class, priority = 500)
public abstract class ModelPartMixin implements BendablePart {

    @Shadow
    @Final
    private List<ModelPart.Cube> cubes;

    @Unique
    @Nullable
    private Bend playeremotes$bend;
    @Unique
    @Nullable
    private LimbMesh[] playeremotes$meshes;

    @Override
    @Nullable
    public Bend playeremotes$bend() {
        return this.playeremotes$bend;
    }

    @Override
    public void playeremotes$setBend(@Nullable Bend bend) {
        this.playeremotes$bend = bend;
    }

    //? if >=1.21 {
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void playeremotes$compileBent(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, int color, CallbackInfo ci) {
        LimbMesh[] meshes = this.playeremotes$meshes();
        if (meshes != null) {
            for (LimbMesh mesh : meshes) {
                mesh.render(pose, consumer, this.playeremotes$bend, light, overlay, color);
            }

            ci.cancel();
        }
    }
    //?} else {
    /*@Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void playeremotes$compileBent(PoseStack.Pose pose, VertexConsumer consumer, int light, int overlay, float red, float green,
                                          float blue, float alpha, CallbackInfo ci) {
        LimbMesh[] meshes = this.playeremotes$meshes();
        if (meshes != null) {
            for (LimbMesh mesh : meshes) {
                mesh.render(pose, consumer, this.playeremotes$bend, light, overlay, red, green, blue, alpha);
            }

            ci.cancel();
        }
    }
    *///?}

    // cut cubes while bent, null to draw it like vanilla
    @Unique
    @Nullable
    private LimbMesh[] playeremotes$meshes() {
        Bend bend = EmoteRenderer.activeBend((ModelPart) (Object) this);
        if (bend == null) {
            return null;
        }

        LimbMesh[] meshes = LimbMesh.of(this.cubes, this.playeremotes$meshes, bend.jointY);
        if (meshes != null) {
            this.playeremotes$meshes = meshes;
        }

        return meshes;
    }

    // posing a part from scratch straightens it, an emote bends it again after
    @Inject(method = "resetPose", at = @At("HEAD"))
    private void playeremotes$straighten(CallbackInfo ci) {
        if (this.playeremotes$bend != null) {
            this.playeremotes$bend.active = false;
        }
    }
}
