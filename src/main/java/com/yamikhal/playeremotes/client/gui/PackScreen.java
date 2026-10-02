package com.yamikhal.playeremotes.client.gui;

import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.client.config.EmoteConfig;
import com.yamikhal.playeremotes.client.emote.EmotePack;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// enables and disables loaded emote packs, disabled packs are hidden from the emote lists
public class PackScreen extends EmoteScreen {

    private static final int ROW_HEIGHT = 22;
    private static final int BOX_SIZE = 12;

    private final List<EmotePack> packs = EmoteRegistry.packs();
    private int x;
    private int y;
    private int listWidth;
    private int listHeight;
    private double scroll;
    private boolean changed;

    public PackScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.playeremotes.packs"), parent);
    }

    @Override
    protected void init() {
        this.listWidth = Math.min(this.width - 40, 260);
        this.listHeight = Math.max(44, Math.min(this.height - 100, 176));
        this.x = (this.width - this.listWidth) / 2;
        this.y = (this.height - this.listHeight) / 2;
        this.clampScroll();

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(this.width / 2 - 50, this.y + this.listHeight + 8, 100, 20)
                .build());
    }

    @Override
    protected void renderContent(Canvas canvas, int mouseX, int mouseY, float partialTick) {
        canvas.centeredText(this.font, this.title, this.width / 2, this.y - 14, 0xFFFFFFFF);
        canvas.fill(this.x, this.y, this.x + this.listWidth, this.y + this.listHeight, 0x66000000);
        canvas.scissor(this.x, this.y, this.x + this.listWidth, this.y + this.listHeight);
        EmoteConfig config = PlayerEmotesClient.config();
        EmotePack hovered = this.packAt(mouseX, mouseY);
        for (int i = 0; i < this.packs.size(); i++) {
            int top = this.y + i * ROW_HEIGHT - (int) this.scroll;
            if (top + ROW_HEIGHT < this.y) continue;
            if (top > this.y + this.listHeight) break;

            EmotePack pack = this.packs.get(i);
            boolean enabled = !config.disabledPacks.contains(pack.id());
            if (pack == hovered) {
                canvas.fill(this.x + 1, top + 1, this.x + this.listWidth - 1, top + ROW_HEIGHT - 1, 0x80424242);
            }

            String name = this.font.substrByWidth(pack.name(), this.listWidth - BOX_SIZE - 18).getString();
            canvas.text(this.font, name, this.x + 6, top + 7, enabled ? 0xFFFFFFFF : 0xFF808080, true);
            canvas.checkbox(this.x + this.listWidth - BOX_SIZE - 6, top + (ROW_HEIGHT - BOX_SIZE) / 2, BOX_SIZE, enabled);
        }

        if (this.packs.isEmpty()) {
            canvas.centeredText(this.font, Component.translatable("playeremotes.list.empty"), this.width / 2, this.y + this.listHeight / 2 - 4, 0xFF909090);
        }

        canvas.endScissor();
    }

    @Override
    protected void renderOverlay(Canvas canvas, int mouseX, int mouseY) {
        EmotePack pack = this.packAt(mouseX, mouseY);
        if (pack != null) {
            canvas.tooltip(this.font, EmoteList.packInfo(this.font, pack), mouseX, mouseY, this.width, this.height);
        }
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        EmotePack pack = this.packAt(mouseX, mouseY);
        if (button != 0 || pack == null) {
            return false;
        }

        EmoteConfig config = PlayerEmotesClient.config();
        if (!config.disabledPacks.remove(pack.id())) {
            config.disabledPacks.add(pack.id());
        }

        this.changed = true;
        return true;
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        this.scroll -= amount * ROW_HEIGHT;
        this.clampScroll();
        return true;
    }

    @Override
    public void removed() {
        if (this.changed) {
            PlayerEmotesClient.config().save();
        }
    }

    @Nullable
    private EmotePack packAt(double mouseX, double mouseY) {
        if (mouseX < this.x || mouseX >= this.x + this.listWidth || mouseY < this.y || mouseY >= this.y + this.listHeight) {
            return null;
        }

        int index = (int) ((mouseY - this.y + this.scroll) / ROW_HEIGHT);
        return index >= 0 && index < this.packs.size() ? this.packs.get(index) : null;
    }

    private void clampScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.packs.size() * ROW_HEIGHT - this.listHeight)));
    }
}
