package com.yamikhal.playeremotes.client.gui;

import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.client.config.EmoteConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

// client settings, laid out like PackScreen: a list of check box rows, each explained by a tooltip
public class SettingsScreen extends EmoteScreen {

    private static final int ROW_HEIGHT = 22;
    private static final int BOX_SIZE = 12;
    private static final int TOOLTIP_WIDTH = 200;

    private static final List<Setting> SETTINGS = List.of(
            new Setting("playeremotes.settings.own_sounds", config -> config.playEmoteSounds,
                    config -> config.playEmoteSounds = !config.playEmoteSounds),
            new Setting("playeremotes.settings.other_sounds", config -> config.hearOtherSounds,
                    config -> config.hearOtherSounds = !config.hearOtherSounds),
            new Setting("playeremotes.settings.other_emotes", config -> config.showOtherEmotes, config -> {
                config.showOtherEmotes = !config.showOtherEmotes;
                PlayerEmotesClient.onShowOtherEmotesChanged();
            }),
            new Setting("playeremotes.settings.icons", config -> config.showIcons,
                    config -> config.showIcons = !config.showIcons),
            new Setting("playeremotes.settings.third_person", config -> config.thirdPersonEmotes,
                    config -> config.thirdPersonEmotes = !config.thirdPersonEmotes),
            new Setting("playeremotes.settings.hold_wheel", config -> config.holdToOpenWheel,
                    config -> config.holdToOpenWheel = !config.holdToOpenWheel),
            new Setting("playeremotes.settings.partner_requests", config -> config.acceptRequests, config -> {
                config.acceptRequests = !config.acceptRequests;
                PlayerEmotesClient.onAcceptRequestsChanged();
            }));

    private int x;
    private int y;
    private int listWidth;
    private int listHeight;
    private boolean changed;

    public SettingsScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.playeremotes.settings"), parent);
    }

    @Override
    protected void init() {
        this.listWidth = Math.min(this.width - 40, 260);
        this.listHeight = SETTINGS.size() * ROW_HEIGHT;
        this.x = (this.width - this.listWidth) / 2;
        this.y = (this.height - this.listHeight) / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> this.onClose())
                .bounds(this.width / 2 - 50, this.y + this.listHeight + 8, 100, 20)
                .build());
    }

    @Override
    protected void renderContent(Canvas canvas, int mouseX, int mouseY, float partialTick) {
        canvas.centeredText(this.font, this.title, this.width / 2, this.y - 14, 0xFFFFFFFF);
        canvas.fill(this.x, this.y, this.x + this.listWidth, this.y + this.listHeight, 0x66000000);
        EmoteConfig config = PlayerEmotesClient.config();
        Setting hovered = this.settingAt(mouseX, mouseY);
        for (int i = 0; i < SETTINGS.size(); i++) {
            Setting setting = SETTINGS.get(i);
            int top = this.y + i * ROW_HEIGHT;
            boolean on = setting.value.test(config);
            if (setting == hovered) {
                canvas.fill(this.x + 1, top + 1, this.x + this.listWidth - 1, top + ROW_HEIGHT - 1, 0x80424242);
            }

            String name = this.font.substrByWidth(Component.translatable(setting.key), this.listWidth - BOX_SIZE - 18).getString();
            canvas.text(this.font, name, this.x + 6, top + 7, on ? 0xFFFFFFFF : 0xFF808080, true);
            canvas.checkbox(this.x + this.listWidth - BOX_SIZE - 6, top + (ROW_HEIGHT - BOX_SIZE) / 2, BOX_SIZE, on);
        }
    }

    @Override
    protected void renderOverlay(Canvas canvas, int mouseX, int mouseY) {
        Setting setting = this.settingAt(mouseX, mouseY);
        if (setting == null) {
            return;
        }

        List<Canvas.Line> lines = new ArrayList<>();
        lines.add(new Canvas.Line(Component.translatable(setting.key).getString(), 0xFFFFFFFF));
        for (String line : Canvas.wrap(this.font, Component.translatable(setting.key + ".tooltip").getString(), TOOLTIP_WIDTH)) {
            lines.add(new Canvas.Line(line, 0xFFA0A0A0));
        }

        canvas.tooltip(this.font, lines, mouseX, mouseY, this.width, this.height);
    }

    @Override
    protected boolean onClick(double mouseX, double mouseY, int button) {
        Setting setting = this.settingAt(mouseX, mouseY);
        if (button != 0 || setting == null) {
            return false;
        }

        setting.toggle.accept(PlayerEmotesClient.config());
        this.changed = true;
        return true;
    }

    @Override
    public void removed() {
        if (this.changed) {
            PlayerEmotesClient.config().save();
        }
    }

    @Nullable
    private Setting settingAt(double mouseX, double mouseY) {
        if (mouseX < this.x || mouseX >= this.x + this.listWidth || mouseY < this.y || mouseY >= this.y + this.listHeight) {
            return null;
        }

        int index = (int) ((mouseY - this.y) / ROW_HEIGHT);
        return index >= 0 && index < SETTINGS.size() ? SETTINGS.get(index) : null;
    }

    private record Setting(String key, Predicate<EmoteConfig> value, Consumer<EmoteConfig> toggle) {}
}
