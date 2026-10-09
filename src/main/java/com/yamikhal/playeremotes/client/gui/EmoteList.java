package com.yamikhal.playeremotes.client.gui;

import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.config.EmoteConfig;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmotePack;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.preview.EmotePreview;
import com.yamikhal.playeremotes.client.preview.PreviewRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

// scrollable, searchable emote list with icon, name, description and author, one collapsible section per enabled
// pack below "Recently Used" (folded by default). hovering a pack header shows the pack info, each emote has a
// 3D preview that plays while selected
public final class EmoteList {

    private static final int ROW_HEIGHT = 36;
    private static final int HEADER_HEIGHT = 18;
    private static final int PREVIEW_HEIGHT = 32;
    private static final int PREVIEW_WIDTH = 40;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int TOOLTIP_WIDTH = 200;
    // id of "Recently Used", not a real pack
    private static final String RECENT = "#recent";

    private final Consumer<Emote> onClick;
    private final List<Row> rows = new ArrayList<>();
    private List<EmotePack> packs = List.of();
    private String query = "";
    private int contentHeight;
    private int x;
    private int y;
    private int width;
    private int height;
    private double scroll;
    @Nullable
    private Emote selected;
    // shown on the selected row, e.g. how to play it
    @Nullable
    private Component selectedHint;

    public EmoteList(Consumer<Emote> onClick) {
        this.onClick = onClick;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.clampScroll();
    }

    public void setPacks(List<EmotePack> packs) {
        this.packs = packs;
        this.rebuild();
    }

    public void filter(String query) {
        this.query = query.toLowerCase(Locale.ROOT).trim();
        this.scroll = 0;
        this.rebuild();
    }

    @Nullable
    public Emote selected() {
        return this.selected;
    }

    public void setSelected(@Nullable Emote emote) {
        this.selected = emote;
    }

    public void setSelectedHint(@Nullable Component hint) {
        this.selectedHint = hint;
    }

    public void render(Canvas canvas, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        canvas.fill(this.x, this.y, this.x + this.width, this.y + this.height, 0x66000000);
        canvas.scissor(this.x, this.y, this.x + this.width, this.y + this.height);

        int rowWidth = this.width - SCROLLBAR_WIDTH - 2;
        Row hovered = this.rowAt(mouseX, mouseY);
        for (Row row : this.rows) {
            int top = this.y + row.top - (int) this.scroll;
            if (top + row.height < this.y) continue;
            if (top > this.y + this.height) break;

            if (row.pack != null) {
                this.renderHeader(canvas, font, row, top, rowWidth, row == hovered);
                continue;
            }

            if (row.emote == this.selected) {
                canvas.fill(this.x + 1, top + 1, this.x + rowWidth, top + ROW_HEIGHT - 1, 0xFF3C6E3C);
            } else if (row == hovered) {
                canvas.fill(this.x + 1, top + 1, this.x + rowWidth, top + ROW_HEIGHT - 1, 0x80424242);
            }

            this.renderRow(canvas, font, row.emote, this.x + 2, top + 2, rowWidth - 4);
        }

        if (this.rows.isEmpty()) {
            canvas.centeredText(font, Component.translatable("playeremotes.list.empty"),
                    this.x + this.width / 2, this.y + this.height / 2 - 4, 0xFF909090);
        }

        canvas.endScissor();
        this.renderScrollbar(canvas);
    }

    // pack info of a hovered section header, call after all widgets are drawn
    public void renderTooltip(Canvas canvas, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        Row row = this.rowAt(mouseX, mouseY);
        if (row == null || row.pack == null || row.pack.id().equals(RECENT)) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        canvas.tooltip(font, packInfo(font, row.pack), mouseX, mouseY, screenWidth, screenHeight);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= this.x && mouseY >= this.y && mouseX < this.x + this.width && mouseY < this.y + this.height;
    }

    public boolean click(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        Row row = this.rowAt(mouseX, mouseY);
        if (row == null) {
            return false;
        }

        if (row.pack != null) {
            if (!this.query.isEmpty()) {
                return true;
            }

            EmoteConfig config = PlayerEmotesClient.config();
            if (row.pack.id().equals(RECENT)) {
                config.recentExpanded = !config.recentExpanded;
            } else if (!config.collapsedPacks.remove(row.pack.id())) {
                config.collapsedPacks.add(row.pack.id());
            }

            config.save();
            this.rebuild();
            return true;
        }

        this.onClick.accept(row.emote);
        return true;
    }

    public boolean scroll(double mouseX, double mouseY, double amount) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        this.scroll -= amount * ROW_HEIGHT;
        this.clampScroll();
        return true;
    }

    private void rebuild() {
        EmoteConfig config = PlayerEmotesClient.config();
        this.rows.clear();
        int top = 0;
        if (this.query.isEmpty()) {
            List<Emote> recent = recentEmotes(config);
            if (!recent.isEmpty()) {
                EmotePack section = new EmotePack(RECENT, Component.translatable("playeremotes.list.recent"),
                        Component.empty(), "", "", recent);
                this.rows.add(new Row(section, null, recent.size(), top, HEADER_HEIGHT));
                top += HEADER_HEIGHT;
                if (config.recentExpanded) {
                    for (Emote emote : recent) {
                        this.rows.add(new Row(null, emote, 0, top, ROW_HEIGHT));
                        top += ROW_HEIGHT;
                    }
                }
            }
        }

        for (EmotePack pack : this.packs) {
            if (config.disabledPacks.contains(pack.id())) continue;

            List<Emote> matches = new ArrayList<>();
            for (Emote emote : pack.emotes()) {
                if (this.query.isEmpty() || emote.searchText().contains(this.query)) {
                    matches.add(emote);
                }
            }

            if (matches.isEmpty() && !this.query.isEmpty()) continue;

            this.rows.add(new Row(pack, null, matches.size(), top, HEADER_HEIGHT));
            top += HEADER_HEIGHT;
            // searching shows every match, also in collapsed sections
            if (!this.query.isEmpty() || !config.collapsedPacks.contains(pack.id())) {
                for (Emote emote : matches) {
                    this.rows.add(new Row(null, emote, 0, top, ROW_HEIGHT));
                    top += ROW_HEIGHT;
                }
            }
        }

        this.contentHeight = top;
        this.clampScroll();
    }

    private void renderHeader(Canvas canvas, Font font, Row row, int top, int rowWidth, boolean hovered) {
        boolean expanded = isExpanded(row.pack, !this.query.isEmpty());
        canvas.fill(this.x + 1, top + 1, this.x + rowWidth, top + HEADER_HEIGHT - 1, hovered ? 0xC0505050 : 0xA0303030);
        String arrow = expanded ? "▼ " : "▶ ";
        String count = " (" + row.count + ")";
        int textWidth = rowWidth - 8 - font.width(arrow) - font.width(count);
        String name = font.substrByWidth(row.pack.name(), textWidth).getString();
        canvas.text(font, arrow + name, this.x + 5, top + 5, 0xFFFFFFFF, true);
        canvas.text(font, count, this.x + 5 + font.width(arrow + name), top + 5, 0xFF909090, true);
    }

    private void renderRow(Canvas canvas, Font font, Emote emote, int left, int top, int rowWidth) {
        int textLeft = left;
        if (PlayerEmotesClient.config().showIcons) {
            EmotePlayback.Frame frame = EmotePreview.frame(emote, emote.id(), emote == this.selected);
            if (frame != null) {
                PreviewRenderer.draw(canvas, frame, left + 1, top, left + 1 + PREVIEW_WIDTH, top + PREVIEW_HEIGHT);
            }

            if (emote.partner() != null) {
                PreviewRenderer.drawPartnerIcon(canvas, left + 1 + PREVIEW_WIDTH, top + PREVIEW_HEIGHT);
            }

            textLeft += PREVIEW_WIDTH + 7;
        }

        int textWidth = rowWidth - (textLeft - left);
        canvas.text(font, font.substrByWidth(emote.name(), textWidth).getString(), textLeft, top + 1, 0xFFFFFFFF, true);
        canvas.text(font, font.substrByWidth(emote.description(), textWidth).getString(), textLeft, top + 12, 0xFF909090, true);
        if (!emote.author().isEmpty()) {
            Component author = Component.translatable("playeremotes.list.author", emote.author());
            canvas.text(font, author, textLeft, top + 23, 0xFFFFD700, true);
        }

        if (emote == this.selected && this.selectedHint != null) {
            canvas.text(font, this.selectedHint, left + rowWidth - font.width(this.selectedHint) - 2, top + 23, 0xFF9EE09E, true);
        }
    }

    private void renderScrollbar(Canvas canvas) {
        if (this.contentHeight <= this.height) {
            return;
        }

        int left = this.x + this.width - SCROLLBAR_WIDTH;
        int thumb = Math.max(16, this.height * this.height / this.contentHeight);
        int thumbTop = this.y + (int) ((this.height - thumb) * (this.scroll / (this.contentHeight - this.height)));
        canvas.fill(left, this.y, left + SCROLLBAR_WIDTH, this.y + this.height, 0xFF000000);
        canvas.fill(left, thumbTop, left + SCROLLBAR_WIDTH, thumbTop + thumb, 0xFF808080);
        canvas.fill(left, thumbTop, left + SCROLLBAR_WIDTH - 1, thumbTop + thumb - 1, 0xFFC0C0C0);
    }

    @Nullable
    private Row rowAt(double mouseX, double mouseY) {
        if (!this.isMouseOver(mouseX, mouseY) || mouseX >= this.x + this.width - SCROLLBAR_WIDTH) {
            return null;
        }

        double contentY = mouseY - this.y + this.scroll;
        for (Row row : this.rows) {
            if (contentY >= row.top && contentY < row.top + row.height) {
                return row;
            }
        }

        return null;
    }

    private void clampScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, this.contentHeight - this.height)));
    }

    // tooltip lines for a pack, from its pack.json
    static List<Canvas.Line> packInfo(Font font, EmotePack pack) {
        List<Canvas.Line> lines = new ArrayList<>();
        String title = pack.name().getString();
        if (!pack.version().isEmpty()) {
            title += " v" + pack.version();
        }

        lines.add(new Canvas.Line(title, 0xFFFFFFFF));
        if (!pack.author().isEmpty()) {
            lines.add(new Canvas.Line(Component.translatable("playeremotes.list.author", pack.author()).getString(), 0xFFFFD700));
        }

        String description = pack.description().getString();
        if (!description.isEmpty()) {
            for (String line : Canvas.wrap(font, description, TOOLTIP_WIDTH)) {
                lines.add(new Canvas.Line(line, 0xFFA0A0A0));
            }
        }

        lines.add(new Canvas.Line(Component.translatable("playeremotes.pack.emotes", pack.emotes().size()).getString(), 0xFF707070));
        return lines;
    }

    // recently played emotes that still exist and are not in a hidden pack
    private static List<Emote> recentEmotes(EmoteConfig config) {
        List<Emote> recent = new ArrayList<>();
        for (ResourceLocation id : config.recent) {
            Emote emote = EmoteRegistry.get(id);
            if (emote != null && !config.disabledPacks.contains(emote.pack())) {
                recent.add(emote);
            }
        }

        return recent;
    }

    private static boolean isExpanded(EmotePack section, boolean searching) {
        EmoteConfig config = PlayerEmotesClient.config();
        if (section.id().equals(RECENT)) {
            return config.recentExpanded;
        }

        return searching || !config.collapsedPacks.contains(section.id());
    }

    // section header or emote
    private record Row(@Nullable EmotePack pack, @Nullable Emote emote, int count, int top, int height) {}
}
