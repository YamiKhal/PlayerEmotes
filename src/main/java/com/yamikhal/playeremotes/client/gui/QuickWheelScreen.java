package com.yamikhal.playeremotes.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.emote.Emote;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

// the emote wheel opened with the keybind. click a slot (or press 1-8) to play it, scroll or use the arrows to switch
// pages. closing it with its key while a slot is hovered plays that slot, with "hold to open" releasing the key does.
// below the wheel sit small text links: stop the running emote, and accept a partner emote request
public class QuickWheelScreen extends EmoteScreen {

    private static final Component STOP = Component.translatable("playeremotes.button.stop");
    private static final int LINK_HEIGHT = 12;

    private final EmoteWheel wheel = new EmoteWheel();
    // releasing the wheel key plays the hovered emote
    private final boolean playOnRelease;
    // the wheel key is still held from opening, its key repeats must not close the wheel
    private boolean keyHeld;
    private int mouseX = -1;
    private int mouseY = -1;
    private List<Button> slotButtons = List.of();
    // invisible buttons under the text links, for clicks, keyboard focus and controller snapping
    private Button stopButton;
    private Button acceptButton;
    // the links' text, the buttons stay blank since an invisible button would still draw its label
    private Component stopText = Component.empty();
    private Component acceptText = Component.empty();

    public QuickWheelScreen(@Nullable Screen parent) {
        this(parent, false);
    }

    // held is whether the wheel key is held down while the wheel opens
    public QuickWheelScreen(@Nullable Screen parent, boolean held) {
        super(Component.translatable("screen.playeremotes.wheel"), parent);
        this.keyHeld = held;
        this.playOnRelease = held && PlayerEmotesClient.config().holdToOpenWheel;
    }

    @Override
    protected void init() {
        // centered on the screen (page number on the crosshair), leaving room for the hovered emote's name above,
        // the links below and the button columns in the bottom corners
        this.wheel.setArea(116, 26, this.width - 232, this.height - 52);
        this.slotButtons = this.wheel.slotButtons(this::play);
        this.slotButtons.forEach(this::addRenderableWidget);
        this.wheel.pageButtons().forEach(this::addRenderableWidget);
        this.stopButton = this.addRenderableWidget(link(button -> {
            PlayerEmotesClient.stop();
            Screens.open(null);
        }, () -> this.stopText));
        this.acceptButton = this.addRenderableWidget(link(button -> {
            PlayerEmotesClient.acceptPartner();
            Screens.open(null);
        }, () -> this.acceptText));

        this.addRenderableWidget(Button.builder(Component.translatable("playeremotes.button.emote_list"),
                        button -> Screens.open(new EmoteListScreen(this)))
                .bounds(this.width - 110, this.height - 28, 100, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("playeremotes.button.settings"),
                        button -> Screens.open(new SettingsScreen(this)))
                .bounds(10, this.height - 28, 100, 20)
                .build());
        this.updateLinks();
    }

    @Override
    protected void renderContent(Canvas canvas, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.updateLinks();
        int hoveredSlot = this.wheel.render(canvas, mouseX, mouseY, false, this.focusedSlot());
        Emote hovered = hoveredSlot >= 0 ? EmoteWheel.emoteAt(hoveredSlot) : null;
        if (hovered != null) {
            canvas.centeredText(this.font, hovered.name(), this.width / 2, this.wheel.top() - 14, 0xFFFFFFFF);
        }

        this.drawLink(canvas, this.stopButton, this.stopText, mouseX, mouseY, 0xFFA0A0A0);
        this.drawLink(canvas, this.acceptButton, this.acceptText, mouseX, mouseY, 0xFF7FD67F);
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        return this.isMouseButton(PlayerEmotesClient.OPEN_WHEEL) && this.wheelKeyPressed();
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        this.wheel.changePage(amount > 0 ? -1 : 1);
        return true;
    }

    @Override
    protected boolean onKey(int keyCode, int scanCode) {
        if (keyCode >= InputConstants.KEY_1 && keyCode <= InputConstants.KEY_8) {
            return this.play(keyCode - InputConstants.KEY_1);
        }

        return this.isKey(PlayerEmotesClient.OPEN_WHEEL) && this.wheelKeyPressed();
    }

    @Override
    protected boolean onKeyRelease(int keyCode, int scanCode) {
        return this.isKey(PlayerEmotesClient.OPEN_WHEEL) && this.wheelKeyReleased();
    }

    @Override
    protected boolean onMouseRelease(double mouseX, double mouseY, int button) {
        return this.isMouseButton(PlayerEmotesClient.OPEN_WHEEL) && this.wheelKeyReleased();
    }

    // shows the links that apply, one under the other below the wheel
    private void updateLinks() {
        int top = this.wheel.top() + this.wheel.height() + 6;
        this.stopText = this.canStop() ? STOP : Component.empty();
        top = this.placeLink(this.stopButton, this.stopText, top);
        PlayerEmotesClient.PendingRequest request = PlayerEmotesClient.pendingRequest();
        this.acceptText = request == null ? Component.empty()
                : Component.translatable("playeremotes.button.accept", request.starterName(), request.emoteName());
        this.placeLink(this.acceptButton, this.acceptText, top);
    }

    private int placeLink(Button button, Component text, int top) {
        button.visible = !text.getString().isEmpty();
        if (!button.visible) {
            return top;
        }

        int textWidth = this.font.width(text) + 8;
        button.setX(this.width / 2 - textWidth / 2);
        button.setY(top);
        button.setWidth(textWidth);
        return top + LINK_HEIGHT + 2;
    }

    private void drawLink(Canvas canvas, Button button, Component label, int mouseX, int mouseY, int color) {
        if (!button.visible) {
            return;
        }

        boolean hover = button.isMouseOver(mouseX, mouseY) || button.isFocused();
        Component text = label.copy().withStyle(hover ? ChatFormatting.UNDERLINE : ChatFormatting.RESET);
        canvas.centeredText(this.font, text, this.width / 2, button.getY() + 2, hover ? 0xFFFFFFFF : color);
    }

    private int focusedSlot() {
        for (int slot = 0; slot < this.slotButtons.size(); slot++) {
            if (this.slotButtons.get(slot).isFocused()) {
                return slot;
            }
        }

        return -1;
    }

    // the stop link is only there while the player is emoting
    private boolean canStop() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && EmotePlayers.isPlaying(player.getUUID());
    }

    private boolean wheelKeyPressed() {
        // key repeat while still held from opening
        if (!this.keyHeld) {
            this.closeOrPlayHovered();
        }

        return true;
    }

    private boolean wheelKeyReleased() {
        if (!this.keyHeld) {
            return false;
        }

        this.keyHeld = false;
        if (this.playOnRelease) {
            this.closeOrPlayHovered();
        }

        return true;
    }

    // closes the wheel, playing the hovered emote if there is one
    private void closeOrPlayHovered() {
        int slot = this.mouseX < 0 ? -1 : this.wheel.slotAt(this.mouseX, this.mouseY);
        if (slot < 0) {
            slot = this.focusedSlot();
        }

        if (slot < 0 || !this.play(slot)) {
            this.onClose();
        }
    }

    private boolean play(int slot) {
        if (slot < 0) {
            return false;
        }

        Emote emote = EmoteWheel.emoteAt(slot);
        if (emote == null) {
            return false;
        }

        PlayerEmotesClient.play(emote);
        Screens.open(null);
        return true;
    }

    private static Button link(Button.OnPress onPress, Supplier<Component> text) {
        Button button = Button.builder(Component.empty(), onPress).bounds(0, 0, 0, LINK_HEIGHT)
                .createNarration(narration -> text.get().copy())
                .build();
        button.setAlpha(0);
        return button;
    }
}
