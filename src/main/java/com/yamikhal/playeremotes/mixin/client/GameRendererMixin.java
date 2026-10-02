package com.yamikhal.playeremotes.mixin.client;

//? if >=1.21.9 {
/*import com.yamikhal.playeremotes.client.preview.PreviewPictures;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

// registers the emote preview picture-in-picture renderers next to the vanilla ones
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;<init>"))
    private List<PictureInPictureRenderer<?>> playeremotes$addPreviewRenderers(List<PictureInPictureRenderer<?>> renderers) {
        return PreviewPictures.withRenderers(renderers);
    }
}
*///?}
