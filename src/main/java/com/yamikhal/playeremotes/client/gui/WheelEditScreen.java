package com.yamikhal.playeremotes.client.gui;

import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.client.config.EmoteConfig;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

// assigns emotes to wheel slots: pick an emote on the left, click a slot on the right, right-clicking a slot clears it
public class WheelEditScreen extends EmoteScreen {

    private final EmoteList list = new EmoteList(this::select);
    private final EmoteWheel wheel = new EmoteWheel();
    private EditBox search;
    private boolean changed;
    private int hintX;
    private int hintY;

    public WheelEditScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.playeremotes.edit"), parent);
    }

    @Override
    protected void init() {
        // list and wheel side by side as one centered block, container-like size at any GUI scale
        int blockHeight = Math.max(80, Math.min(this.height - 100, 200));
        int listWidth = Math.max(120, Math.min(this.width / 2 - 30, 220));
        this.wheel.setArea(0, 0, Math.max(100, this.width - listWidth - 60), blockHeight);
        int blockWidth = listWidth + 16 + this.wheel.width();
        int left = (this.width - blockWidth) / 2;
        int top = (this.height - blockHeight - 52) / 2;
        int wheelLeft = left + listWidth + 16;
        this.wheel.setArea(wheelLeft, top + 26, this.wheel.width() + 2, blockHeight);
        this.wheel.pageButtons().forEach(this::addRenderableWidget);
        this.hintY = top + 6;
        this.hintX = wheelLeft + this.wheel.width() / 2;

        String query = this.search != null ? this.search.getValue() : "";
        this.search = new EditBox(this.font, left, top, listWidth, 20, Component.translatable("playeremotes.search"));
        this.search.setHint(Component.translatable("playeremotes.search"));
        this.search.setValue(query);
        this.search.setResponder(this.list::filter);
        this.addRenderableWidget(this.search);
        this.setInitialFocus(this.search);

        this.list.setBounds(left, top + 26, listWidth, blockHeight);
        this.list.setPacks(EmoteRegistry.packs());
        this.list.filter(query);

        int buttonTop = top + 32 + blockHeight;
        this.addRenderableWidget(Button.builder(iconsLabel(), button -> {
                    EmoteConfig config = PlayerEmotesClient.config();
                    config.showIcons = !config.showIcons;
                    this.changed = true;
                    button.setMessage(iconsLabel());
                })
                .bounds(left + blockWidth - 214, buttonTop, 140, 20)
                .build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(left + blockWidth - 70, buttonTop, 70, 20)
                .build());
    }

    @Override
    protected void renderContent(Canvas canvas, int mouseX, int mouseY, float partialTick) {
        this.list.render(canvas, mouseX, mouseY);
        this.wheel.render(canvas, mouseX, mouseY, this.list.selected() != null);
        Component hint = Component.translatable(this.list.selected() == null
                ? "playeremotes.edit.hint_select" : "playeremotes.edit.hint_assign");
        canvas.centeredText(this.font, hint, this.hintX, this.hintY, 0xFFA0A0A0);
    }

    @Override
    protected void renderOverlay(Canvas canvas, int mouseX, int mouseY) {
        this.list.renderTooltip(canvas, mouseX, mouseY, this.width, this.height);
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        if (this.list.click(mouseX, mouseY, button)) {
            return true;
        }

        int slot = this.wheel.slotAt(mouseX, mouseY);
        if (slot < 0) {
            return false;
        }

        EmoteConfig config = PlayerEmotesClient.config();
        if (button == 1) {
            config.setWheelSlot(EmoteWheel.page(), slot, null);
        } else if (button == 0 && this.list.selected() != null) {
            config.setWheelSlot(EmoteWheel.page(), slot, this.list.selected().id());
        } else {
            return false;
        }

        this.changed = true;
        return true;
    }

    @Override
    protected boolean onScroll(double mouseX, double mouseY, double amount) {
        if (this.list.scroll(mouseX, mouseY, amount)) {
            return true;
        }

        if (this.wheel.isMouseOver(mouseX, mouseY)) {
            this.wheel.changePage(amount > 0 ? -1 : 1);
            return true;
        }

        return false;
    }

    @Override
    public void removed() {
        if (this.changed) {
            PlayerEmotesClient.config().save();
        }
    }

    private void select(Emote emote) {
        this.list.setSelected(emote);
    }

    private static Component iconsLabel() {
        return CommonComponents.optionNameValue(Component.translatable("playeremotes.settings.icons"),
                PlayerEmotesClient.config().showIcons ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
