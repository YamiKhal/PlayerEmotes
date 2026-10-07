package com.yamikhal.playeremotes.client.preview;

import org.joml.Vector3f;

//? if >=1.21.9 {
/*import com.mojang.blaze3d.platform.Lighting;
import com.yamikhal.playeremotes.mixin.client.LightingInvoker;
*///?} else
import com.mojang.blaze3d.systems.RenderSystem;

// lights for emote previews. vanilla inventory lights come mostly from above, made for a player standing still:
// a face tilted toward the viewer and a bit down gets no light, so swinging limbs flicker between bright and dark.
// these come from the viewer instead, key light a bit up and left plus a weak one from above, faces toward the
// viewer stay lit and only faces turning away darken. GUI space: y down, z toward the viewer, length of a light
// is its strength
final class PreviewLighting {

    private static final Vector3f KEY = new Vector3f(-0.2F, -0.25F, 1).normalize();
    private static final Vector3f FILL = new Vector3f(0, -0.3F, 0);

    //? if >=1.21.9 {
    /*// own set of light buffers, only its entity in UI entry gets our lights, render thread only
    private static Lighting lighting;
    *///?}

    private PreviewLighting() {}

    static void setup() {
        //? if >=1.21.9 {
        /*if (lighting == null) {
            lighting = new Lighting();
            ((LightingInvoker) (Object) lighting).playeremotes$updateBuffer(Lighting.Entry.ENTITY_IN_UI, KEY, FILL);
        }

        lighting.setupFor(Lighting.Entry.ENTITY_IN_UI);
        *///?} else
        RenderSystem.setShaderLights(KEY, FILL);
    }
}
