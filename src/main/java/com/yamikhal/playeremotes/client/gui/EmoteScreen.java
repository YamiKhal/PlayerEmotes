package com.yamikhal.playeremotes.client.gui;

import com.yamikhal.playeremotes.client.preview.EmotePreview;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else
import net.minecraft.client.gui.GuiGraphics;
//? if >=1.21.9 {
/*import com.mojang.blaze3d.platform.InputConstants;
import com.yamikhal.playeremotes.client.preview.PreviewPictures;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
*///?}

// base for all emote screens. turns the input and render callbacks that changed signature between versions into
// on* hooks, draws a plain dimmed backdrop instead of the vanilla (blurred) background
public abstract class EmoteScreen extends Screen {

    @Nullable
    protected final Screen parent;
    // input event being handled, for matching key mappings (API changed in 1.21.9)
    //? if >=1.21.9 {
    /*@Nullable
    private KeyEvent keyEvent;
    @Nullable
    private MouseButtonEvent mouseEvent;
    *///?} else {
    private int keyCode = -1;
    private int scanCode = -1;
    private int mouseButton = -1;
    //?}

    protected EmoteScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    public void added() {
        super.added();
        EmotePreview.reset();
    }

    // draws custom widgets, vanilla widgets (buttons, edit boxes) come after
    protected abstract void renderContent(Canvas canvas, int mouseX, int mouseY, float partialTick);

    // draws on top of everything else, e.g. tooltips
    protected void renderOverlay(Canvas canvas, int mouseX, int mouseY) {}

    // returns whether the click was handled
    protected boolean onClick(double mouseX, double mouseY, int button) {
        return false;
    }

    // amount is positive when scrolling up
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    protected boolean onKey(int keyCode, int scanCode) {
        return false;
    }

    protected boolean onKeyRelease(int keyCode, int scanCode) {
        return false;
    }

    protected boolean onMouseRelease(double mouseX, double mouseY, int button) {
        return false;
    }

    // whether the key pressed or released belongs to the key mapping
    protected boolean isKey(KeyMapping key) {
        //? if >=1.21.9 {
        /*return this.keyEvent != null && key.matches(this.keyEvent);
        *///?} else
        return this.keyCode >= 0 && key.matches(this.keyCode, this.scanCode);
    }

    // whether the mouse button pressed or released belongs to the key mapping
    protected boolean isMouseButton(KeyMapping key) {
        //? if >=1.21.9 {
        /*return this.mouseEvent != null && key.matchesMouse(this.mouseEvent);
        *///?} else
        return this.mouseButton >= 0 && key.matchesMouse(this.mouseButton);
    }

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        Canvas canvas = new Canvas(graphics);
        this.draw(canvas, mouseX, mouseY, partialTick);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.renderOverlay(canvas, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // backdrop is drawn with the content
    }
    *///?} else {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Canvas canvas = new Canvas(graphics);
        this.draw(canvas, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderOverlay(canvas, mouseX, mouseY);
    }

    //? if >=1.20.2 {
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // backdrop is drawn with the content
    }
    //?}
    //?}

    //? if >=1.21.9 {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }

        this.mouseEvent = event;
        try {
            return this.onClick(event.x(), event.y(), normalizeButton(event.button()));
        } finally {
            this.mouseEvent = null;
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.mouseEvent = event;
        try {
            if (this.onMouseRelease(event.x(), event.y(), normalizeButton(event.button()))) {
                return true;
            }
        } finally {
            this.mouseEvent = null;
        }

        return super.mouseReleased(event);
    }
    *///?} else {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        this.mouseButton = button;
        try {
            return this.onClick(mouseX, mouseY, button);
        } finally {
            this.mouseButton = -1;
        }
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.mouseButton = button;
        try {
            if (this.onMouseRelease(mouseX, mouseY, button)) {
                return true;
            }
        } finally {
            this.mouseButton = -1;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }
    //?}

    //? if >=1.20.2 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.onScroll(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return this.onScroll(mouseX, mouseY, amount) || super.mouseScrolled(mouseX, mouseY, amount);
    }
    *///?}

    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(KeyEvent event) {
        if (super.keyPressed(event)) {
            return true;
        }

        this.keyEvent = event;
        try {
            //? if >=26.3 {
            /^return this.onKey(event.key(), event.keycode());
            ^///?} else
            return this.onKey(event.key(), event.scancode());
        } finally {
            this.keyEvent = null;
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        this.keyEvent = event;
        try {
            //? if >=26.3 {
            /^boolean handled = this.onKeyRelease(event.key(), event.keycode());
            ^///?} else
            boolean handled = this.onKeyRelease(event.key(), event.scancode());
            if (handled) {
                return true;
            }
        } finally {
            this.keyEvent = null;
        }

        return super.keyReleased(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        this.keyCode = keyCode;
        this.scanCode = scanCode;
        try {
            return this.onKey(keyCode, scanCode);
        } finally {
            this.keyCode = this.scanCode = -1;
        }
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        try {
            if (this.onKeyRelease(keyCode, scanCode)) {
                return true;
            }
        } finally {
            this.keyCode = this.scanCode = -1;
        }

        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    //?}

    @Override
    public void onClose() {
        Screens.open(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void draw(Canvas canvas, int mouseX, int mouseY, float partialTick) {
        //? if >=1.21.9
        /*PreviewPictures.beginFrame();*/
        Canvas.resetScissors();
        canvas.gradient(0, 0, this.width, this.height, 0xB0101010, 0xC8101010);
        this.renderContent(canvas, mouseX, mouseY, partialTick);
    }

    //? if >=1.21.9 {
    /*// platform mouse button to 0 = left, 1 = right, 2 = middle
    private static int normalizeButton(int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            return 0;
        }

        if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
            return 1;
        }

        if (button == InputConstants.MOUSE_BUTTON_MIDDLE) {
            return 2;
        }

        return button;
    }
    *///?}
}
