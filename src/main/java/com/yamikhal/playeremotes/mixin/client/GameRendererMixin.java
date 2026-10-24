package com.yamikhal.playeremotes.mixin.client;

//? if >=1.21.9 {
/*import com.yamikhal.playeremotes.client.preview.PreviewPictures;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

// registers the preview picture-in-picture renderers next to the vanilla ones
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    // full descriptor on obfuscated versions, loom won't remap a bare <init> target
    //? if <26.1 {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;<init>(Lnet/minecraft/client/gui/render/state/GuiRenderState;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;Ljava/util/List;)V"))
    //?} else
    /^@ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;<init>"))^/
    private List<PictureInPictureRenderer<?>> playeremotes$addPreviewRenderers(List<PictureInPictureRenderer<?>> renderers) {
        return PreviewPictures.withRenderers(renderers);
    }
}
*///?}
