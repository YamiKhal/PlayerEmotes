package com.yamikhal.playeremotes.mixin.client;

//? if >=1.21.9 {
/*import com.mojang.blaze3d.platform.Lighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

//? if >=26.3 {
/^import org.joml.Vector3fc;
^///?} else
import org.joml.Vector3f;

// previews keep their own light buffer with other lights (see PreviewLighting)
@Mixin(Lighting.class)
public interface LightingInvoker {

    //? if >=26.3 {
    /^@Invoker("updateBuffer")
    void playeremotes$updateBuffer(Lighting.Entry entry, Vector3fc light0, Vector3fc light1);
    ^///?} else {
    @Invoker("updateBuffer")
    void playeremotes$updateBuffer(Lighting.Entry entry, Vector3f light0, Vector3f light1);
    //?}
}
*///?}
