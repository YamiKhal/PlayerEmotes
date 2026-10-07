package com.yamikhal.playeremotes.mixin.client;

//? if >=1.21.9 {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
//? if >=26.1 {
/^import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
^///?} else {
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
//?}

// previews submit their own picture-in-picture elements, needs the GUI render state
//? if >=26.1 {
/^@Mixin(GuiGraphicsExtractor.class)
^///?} else
@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {

    @Accessor("guiRenderState")
    GuiRenderState playeremotes$guiRenderState();
}
*///?}
