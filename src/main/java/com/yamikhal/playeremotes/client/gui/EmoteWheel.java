package com.yamikhal.playeremotes.client.gui;

import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.config.EmoteConfig;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.preview.EmotePreview;
import com.yamikhal.playeremotes.client.preview.PreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

// emote picker: 8 bordered slots around a page switcher, EmoteConfig#PAGES pages. slot 0 at the bottom, going
// counter clockwise (right, top, left), number keys keep their place from the radial wheel. slots show a 3D
// preview that plays while hovered
public final class EmoteWheel {

    // slot width relative to its height
    private static final float ASPECT = 1.55F;
    // space between slots relative to slot height
    private static final float GAP = 0.12F;
    private static final int BUTTON_SIZE = 20;
    // largest slot height in GUI units, keeps a container like size at any GUI scale
    private static final int MAX_CELL_HEIGHT = 40;
    // grid column and row of each slot
    private static final int[][] CELLS = {{1, 2}, {2, 2}, {2, 1}, {2, 0}, {1, 0}, {0, 0}, {0, 1}, {0, 2}};

    // shared between screens so the wheel remembers the page
    private static int page;

    private int x;
    private int y;
    private int cellWidth;
    private int cellHeight;
    private int gap;

    // centers the wheel in the area, as large as fits up to its max size
    public void setArea(int areaX, int areaY, int areaWidth, int areaHeight) {
        int fromHeight = (int) (areaHeight / (3 + 2 * GAP));
        int fromWidth = (int) (areaWidth / (3 * ASPECT + 2 * GAP));
        this.cellHeight = Math.max(16, Math.min(MAX_CELL_HEIGHT, Math.min(fromHeight, fromWidth)));
        this.cellWidth = (int) (this.cellHeight * ASPECT);
        this.gap = Math.max(4, (int) (this.cellHeight * GAP));
        this.x = areaX + (areaWidth - this.width()) / 2;
        this.y = areaY + (areaHeight - this.height()) / 2;
    }

    public int width() {
        return 3 * this.cellWidth + 2 * this.gap;
    }

    public int height() {
        return 3 * this.cellHeight + 2 * this.gap;
    }

    public int left() {
        return this.x;
    }

    public int top() {
        return this.y;
    }

    public void changePage(int delta) {
        page = Math.floorMod(page + delta, EmoteConfig.PAGES);
    }

    // previous/next page buttons in the center, add them after setArea
    public List<Button> pageButtons() {
        int centerX = this.x + this.width() / 2;
        int buttonY = this.y + this.height() / 2 - BUTTON_SIZE / 2;
        int offset = Math.min(this.cellWidth / 2 - BUTTON_SIZE, 30);
        return List.of(
                Button.builder(Component.literal("<"), button -> this.changePage(-1))
                        .bounds(centerX - offset - BUTTON_SIZE, buttonY, BUTTON_SIZE, BUTTON_SIZE).build(),
                Button.builder(Component.literal(">"), button -> this.changePage(1))
                        .bounds(centerX + offset, buttonY, BUTTON_SIZE, BUTTON_SIZE).build());
    }

    // highlightEmpty highlights empty slots on hover (true when editing), returns hovered slot or -1
    public int render(Canvas canvas, int mouseX, int mouseY, boolean highlightEmpty) {
        return this.render(canvas, mouseX, mouseY, highlightEmpty, -1);
    }

    // focused is the keyboard focused slot, highlighted when the mouse is not over one
    public int render(Canvas canvas, int mouseX, int mouseY, boolean highlightEmpty, int focused) {
        Font font = Minecraft.getInstance().font;
        int hovered = this.slotAt(mouseX, mouseY);
        if (hovered < 0) {
            hovered = focused;
        }

        boolean previews = PlayerEmotesClient.config().showIcons;
        for (int slot = 0; slot < EmoteConfig.SLOTS; slot++) {
            int left = this.cellX(CELLS[slot][0]);
            int top = this.cellY(CELLS[slot][1]);
            Emote emote = emoteAt(slot);
            boolean highlight = slot == hovered && (highlightEmpty || emote != null);
            canvas.frame(left, top, this.cellWidth, this.cellHeight,
                    highlight ? 0x30FFFFFF : 0x28000000, highlight ? 0xFFFFFFFF : 0x80A0A0A0);
            if (emote == null) continue;

            EmotePlayback.Frame frame = previews ? EmotePreview.frame(emote, slot, slot == hovered) : null;
            if (frame != null) {
                PreviewRenderer.draw(canvas, frame, left + 1, top + 1, left + this.cellWidth - 1, top + this.cellHeight - 1);
            } else {
                List<String> lines = Canvas.wrap(font, emote.name().getString(), this.cellWidth - 8);
                int textTop = top + (this.cellHeight - lines.size() * 10) / 2 + 1;
                for (int i = 0; i < lines.size(); i++) {
                    String line = lines.get(i);
                    canvas.text(font, line, left + (this.cellWidth - font.width(line)) / 2, textTop + i * 10, 0xFFFFFFFF, true);
                }
            }

            if (emote.partner() != null) {
                PreviewRenderer.drawPartnerIcon(canvas, left + this.cellWidth - 1, top + this.cellHeight - 1);
            }
        }

        String pageText = String.valueOf(page + 1);
        canvas.text(font, pageText, this.x + (this.width() - font.width(pageText)) / 2, this.y + this.height() / 2 - 4, 0xFFFFFFFF, true);
        return hovered;
    }

    // invisible buttons over the slots for keyboard focus (Tab, arrows, Enter), narration and controller mods that
    // snap to buttons, add them after setArea
    public List<Button> slotButtons(IntConsumer onPress) {
        List<Button> buttons = new ArrayList<>();
        for (int slot = 0; slot < EmoteConfig.SLOTS; slot++) {
            int index = slot;
            Button button = Button.builder(Component.empty(), pressed -> onPress.accept(index))
                    .bounds(this.cellX(CELLS[slot][0]), this.cellY(CELLS[slot][1]), this.cellWidth, this.cellHeight)
                    .createNarration(narration -> {
                        Emote emote = emoteAt(index);
                        return emote != null ? emote.name().copy() : Component.translatable("playeremotes.wheel.empty_slot", index + 1);
                    })
                    .build();
            // wheel draws the slots, button only takes input
            button.setAlpha(0);
            buttons.add(button);
        }

        return buttons;
    }

    // slot under the mouse, or -1
    public int slotAt(double mouseX, double mouseY) {
        for (int slot = 0; slot < EmoteConfig.SLOTS; slot++) {
            int left = this.cellX(CELLS[slot][0]);
            int top = this.cellY(CELLS[slot][1]);
            if (mouseX >= left && mouseX < left + this.cellWidth && mouseY >= top && mouseY < top + this.cellHeight) {
                return slot;
            }
        }

        return -1;
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width() && mouseY < this.y + this.height();
    }

    public static int page() {
        return page;
    }

    @Nullable
    public static Emote emoteAt(int slot) {
        ResourceLocation id = PlayerEmotesClient.config().wheelSlot(page, slot);
        return id == null ? null : EmoteRegistry.get(id);
    }

    private int cellX(int column) {
        return this.x + column * (this.cellWidth + this.gap);
    }

    private int cellY(int row) {
        return this.y + row * (this.cellHeight + this.gap);
    }
}
