package com.yamikhal.playeremotes.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.6 {
/*import net.minecraft.client.renderer.RenderPipelines;
*///?} elif >=1.21.2 {
/*import net.minecraft.client.renderer.RenderType;
*///?} else
import com.mojang.blaze3d.systems.RenderSystem;

// drawing surface for the emote screens. wraps the GUI graphics object, which changed name and API several
// times, so the rest of the GUI stays version independent. all colors ARGB
public final class Canvas {

    // active scissor areas as {x1, y1, x2, y2}, each already intersected with the one below
    private static final Deque<int[]> SCISSORS = new ArrayDeque<>();

    //? if >=26.1 {
    /*public final GuiGraphicsExtractor graphics;

    public Canvas(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }
    *///?} else {
    public final GuiGraphics graphics;

    public Canvas(GuiGraphics graphics) {
        this.graphics = graphics;
    }
    //?}

    // draws region (u, v, regionWidth, regionHeight) of a texture sized (textureWidth, textureHeight) stretched to
    // (x, y, width, height)
    public void texture(ResourceLocation texture, int x, int y, int width, int height,
                        float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        //? if >=1.21.6 {
        /*this.graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        *///?} elif >=1.21.2 {
        /*this.graphics.blit(RenderType::guiTextured, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        *///?} else {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        this.graphics.blit(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        RenderSystem.disableBlend();
        //?}
    }

    // draws a whole texture into a square
    public void icon(ResourceLocation texture, int x, int y, int size) {
        this.texture(texture, x, y, size, size, 0, 0, 16, 16, 16, 16);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        this.graphics.fill(x1, y1, x2, y2, color);
    }

    public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        this.graphics.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*this.graphics.text(font, text, x, y, color, shadow);
        *///?} else
        this.graphics.drawString(font, text, x, y, color, shadow);
    }

    public void text(Font font, String text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*this.graphics.text(font, text, x, y, color, shadow);
        *///?} else
        this.graphics.drawString(font, text, x, y, color, shadow);
    }

    public void centeredText(Font font, Component text, int centerX, int y, int color) {
        this.text(font, text, centerX - font.width(text) / 2, y, color, true);
    }

    // square check box, filled green when checked
    public void checkbox(int x, int y, int size, boolean checked) {
        this.fill(x, y, x + size, y + size, 0xFFA0A0A0);
        this.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF202020);
        if (checked) {
            this.fill(x + 3, y + 3, x + size - 3, y + size - 3, 0xFF55DD55);
        }
    }

    // box with 1px border and slightly rounded (cut) corners
    public void frame(int x, int y, int width, int height, int background, int border) {
        int x2 = x + width;
        int y2 = y + height;
        this.fill(x + 1, y + 1, x2 - 1, y2 - 1, background);
        this.fill(x + 2, y, x2 - 2, y + 1, border);
        this.fill(x + 2, y2 - 1, x2 - 2, y2, border);
        this.fill(x, y + 2, x + 1, y2 - 2, border);
        this.fill(x2 - 1, y + 2, x2, y2 - 2, border);
        this.fill(x + 1, y + 1, x + 2, y + 2, border);
        this.fill(x2 - 2, y + 1, x2 - 1, y + 2, border);
        this.fill(x + 1, y2 - 2, x + 2, y2 - 1, border);
        this.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, border);
    }

    public void scissor(int x1, int y1, int x2, int y2) {
        int[] outer = SCISSORS.peek();
        if (outer != null) {
            x1 = Math.max(x1, outer[0]);
            y1 = Math.max(y1, outer[1]);
            x2 = Math.max(x1, Math.min(x2, outer[2]));
            y2 = Math.max(y1, Math.min(y2, outer[3]));
        }

        SCISSORS.push(new int[]{x1, y1, x2, y2});
        this.graphics.enableScissor(x1, y1, x2, y2);
    }

    public void endScissor() {
        SCISSORS.poll();
        this.graphics.disableScissor();
    }

    // tooltip box next to the mouse, kept on screen, draw after everything it should cover
    public void tooltip(Font font, List<Line> lines, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (lines.isEmpty()) {
            return;
        }

        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, font.width(line.text()));
        }

        int height = lines.size() * 10 - 2;
        int x = Math.max(4, Math.min(mouseX + 12, screenWidth - width - 8));
        int y = Math.max(4, Math.min(mouseY - 12, screenHeight - height - 8));

        // before 1.21.6 text is batched and drawn after all fills, lift the tooltip above it like vanilla
        //? if >=1.21.6 {
        /*this.graphics.nextStratum();
        *///?} else {
        this.graphics.pose().pushPose();
        this.graphics.pose().translate(0, 0, 400);
        //?}
        this.fill(x - 3, y - 4, x + width + 3, y + height + 4, 0xF0100010);
        this.fill(x - 4, y - 3, x - 3, y + height + 3, 0xF0100010);
        this.fill(x + width + 3, y - 3, x + width + 4, y + height + 3, 0xF0100010);
        this.gradient(x - 3, y - 3, x - 2, y + height + 3, 0x505000FF, 0x5028007F);
        this.gradient(x + width + 2, y - 3, x + width + 3, y + height + 3, 0x505000FF, 0x5028007F);
        this.fill(x - 3, y - 3, x + width + 3, y - 2, 0x505000FF);
        this.fill(x - 3, y + height + 2, x + width + 3, y + height + 3, 0x5028007F);
        for (int i = 0; i < lines.size(); i++) {
            this.text(font, lines.get(i).text(), x, y + i * 10, lines.get(i).color(), true);
        }
        //? if <1.21.6
        this.graphics.pose().popPose();
    }

    // drops scissor areas an interrupted frame left behind, call when a frame starts
    static void resetScissors() {
        SCISSORS.clear();
    }

    // active scissor area as {x1, y1, x2, y2}, or null
    @Nullable
    public static int[] currentScissor() {
        return SCISSORS.peek();
    }

    // splits text into lines at most width pixels wide, breaking between words
    public static List<String> wrap(Font font, String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.width(candidate) > width) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(candidate);
                }
            }

            lines.add(line.toString());
        }

        return lines;
    }

    // text and ARGB color of a tooltip line
    public record Line(String text, int color) {}
}
