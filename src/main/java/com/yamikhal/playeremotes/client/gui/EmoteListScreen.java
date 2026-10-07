package com.yamikhal.playeremotes.client.gui;

import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// every available emote, first click selects it and plays its preview, second click plays it
public class EmoteListScreen extends EmoteScreen {

    private final EmoteList list = new EmoteList(this::click);
    private EditBox search;

    public EmoteListScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.playeremotes.list"), parent);
        this.list.setSelectedHint(Component.translatable("playeremotes.list.play_hint"));
    }

    public void select(Emote emote) {
        this.list.setSelected(emote);
    }

    @Override
    protected void init() {
        // container like size at any GUI scale: search box, list and buttons as one centered block
        int listWidth = Math.min(this.width - 40, 280);
        int listHeight = Math.max(60, Math.min(this.height - 100, 200));
        int left = (this.width - listWidth) / 2;
        int top = (this.height - listHeight - 52) / 2;

        String query = this.search != null ? this.search.getValue() : "";
        this.search = new EditBox(this.font, left, top, listWidth, 20, Component.translatable("playeremotes.search"));
        this.search.setHint(Component.translatable("playeremotes.search"));
        this.search.setValue(query);
        this.search.setResponder(this.list::filter);
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);

        this.list.setBounds(left, top + 26, listWidth, listHeight);
        this.list.setPacks(EmoteRegistry.packs());
        this.list.filter(query);

        int buttonTop = top + 32 + listHeight;
        int buttonWidth = (listWidth - 8) / 3;
        this.addRenderableWidget(Button.builder(Component.translatable("playeremotes.button.packs"),
                        button -> Screens.open(new PackScreen(this)))
                .bounds(left, buttonTop, buttonWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("playeremotes.button.configure"),
                        button -> Screens.open(new WheelEditScreen(this)))
                .bounds(left + buttonWidth + 4, buttonTop, buttonWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left + listWidth - buttonWidth, buttonTop, buttonWidth, 20)
                .build());
    }

    @Override
    protected void renderContent(Canvas canvas, int mouseX, int mouseY, float partialTick) {
        this.list.render(canvas, mouseX, mouseY);
    }

    @Override
    protected void renderOverlay(Canvas canvas, int mouseX, int mouseY) {
        this.list.renderTooltip(canvas, mouseX, mouseY, this.width, this.height);
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        return this.list.click(mouseX, mouseY, button);
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        return this.list.scroll(mouseX, mouseY, amount);
    }

    private void click(Emote emote) {
        if (emote != this.list.selected()) {
            this.list.setSelected(emote);
            return;
        }

        PlayerEmotesClient.play(emote);
        Screens.open(null);
    }
}
