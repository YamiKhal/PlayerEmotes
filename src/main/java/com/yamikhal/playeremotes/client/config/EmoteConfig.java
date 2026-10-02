package com.yamikhal.playeremotes.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yamikhal.playeremotes.PlayerEmotes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// client settings stored in config/playeremotes.json
public final class EmoteConfig {

    public static final int PAGES = 10;
    public static final int SLOTS = 8;
    // most emotes kept in recent
    public static final int RECENT = 10;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private final Path file;
    private final ResourceLocation[][] wheel = new ResourceLocation[PAGES][SLOTS];
    public boolean showIcons = true;
    // other players hear the sounds of our emotes
    public boolean playEmoteSounds = true;
    public boolean hearOtherSounds = true;
    public boolean showOtherEmotes = true;
    // switches to third person while emoting from first person
    public boolean thirdPersonEmotes = true;
    // emotes may bend elbows and knees (see Bend), off draws every limb straight
    public boolean bendLimbs = true;
    // the wheel shows while its key is held and plays the hovered emote on release
    public boolean holdToOpenWheel = false;
    // other players may ask to do partner emotes with us
    public boolean acceptRequests = true;
    // recently played emotes, newest first
    public final List<ResourceLocation> recent = new ArrayList<>();
    // whether the "Recently Used" section of the emote lists is unfolded (folded by default)
    public boolean recentExpanded = false;
    // emote packs hidden from the emote lists
    public final Set<String> disabledPacks = new TreeSet<>();
    // emote pack sections folded in the emote lists
    public final Set<String> collapsedPacks = new TreeSet<>();

    // changes waiting for saveSoon's deferred save, and ticks since the first of them
    private boolean dirty;
    private int dirtyTicks;

    public EmoteConfig(Path file) {
        this.file = file;
    }

    @Nullable
    public ResourceLocation wheelSlot(int page, int slot) {
        return this.wheel[page][slot];
    }

    public void setWheelSlot(int page, int slot, @Nullable ResourceLocation emote) {
        this.wheel[page][slot] = emote;
    }

    // moves an emote to the front of recent
    public void markRecent(ResourceLocation emote) {
        this.recent.remove(emote);
        this.recent.add(0, emote);
        while (this.recent.size() > RECENT) {
            this.recent.remove(this.recent.size() - 1);
        }
    }

    // saves within a few seconds, so frequent small changes (recently used emotes) cost one write
    public void saveSoon() {
        this.dirty = true;
    }

    // call every client tick, also saves pending changes when now is set
    public void tickSave(boolean now) {
        if (!this.dirty) {
            return;
        }

        if (now || ++this.dirtyTicks >= 100) {
            this.save();
        }
    }

    public void load() {
        if (!Files.exists(this.file)) {
            this.save();
            return;
        }

        try (Reader reader = Files.newBufferedReader(this.file)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            this.showIcons = getBoolean(json, "showIcons", this.showIcons);
            this.playEmoteSounds = getBoolean(json, "playEmoteSounds", this.playEmoteSounds);
            this.hearOtherSounds = getBoolean(json, "hearOtherSounds", this.hearOtherSounds);
            this.showOtherEmotes = getBoolean(json, "showOtherEmotes", this.showOtherEmotes);
            this.thirdPersonEmotes = getBoolean(json, "thirdPersonEmotes", this.thirdPersonEmotes);
            this.bendLimbs = getBoolean(json, "bendLimbs", this.bendLimbs);
            this.holdToOpenWheel = getBoolean(json, "holdToOpenWheel", this.holdToOpenWheel);
            this.acceptRequests = getBoolean(json, "acceptRequests", this.acceptRequests);
            this.recentExpanded = getBoolean(json, "recentExpanded", this.recentExpanded);
            if (json.has("recent")) {
                this.recent.clear();
                for (JsonElement element : json.getAsJsonArray("recent")) {
                    ResourceLocation id = PlayerEmotes.parseId(element.getAsString());
                    if (id != null && !this.recent.contains(id) && this.recent.size() < RECENT) {
                        this.recent.add(id);
                    }
                }
            }

            readSet(json, "disabledPacks", this.disabledPacks);
            readSet(json, "collapsedPacks", this.collapsedPacks);
            if (json.has("quickWheel")) {
                JsonArray pages = json.getAsJsonArray("quickWheel");
                for (int page = 0; page < Math.min(PAGES, pages.size()); page++) {
                    JsonArray slots = pages.get(page).getAsJsonArray();
                    for (int slot = 0; slot < Math.min(SLOTS, slots.size()); slot++) {
                        JsonElement value = slots.get(slot);
                        this.wheel[page][slot] = value.isJsonNull() ? null : PlayerEmotes.parseId(value.getAsString());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            PlayerEmotes.LOGGER.error("Failed to read {}, using defaults", this.file, e);
        }
    }

    public void save() {
        this.dirty = false;
        this.dirtyTicks = 0;
        JsonObject json = new JsonObject();
        json.addProperty("showIcons", this.showIcons);
        json.addProperty("playEmoteSounds", this.playEmoteSounds);
        json.addProperty("hearOtherSounds", this.hearOtherSounds);
        json.addProperty("showOtherEmotes", this.showOtherEmotes);
        json.addProperty("thirdPersonEmotes", this.thirdPersonEmotes);
        json.addProperty("bendLimbs", this.bendLimbs);
        json.addProperty("holdToOpenWheel", this.holdToOpenWheel);
        json.addProperty("acceptRequests", this.acceptRequests);
        json.addProperty("recentExpanded", this.recentExpanded);
        JsonArray recentJson = new JsonArray();
        this.recent.forEach(id -> recentJson.add(id.toString()));
        json.add("recent", recentJson);
        json.add("disabledPacks", writeSet(this.disabledPacks));
        json.add("collapsedPacks", writeSet(this.collapsedPacks));
        JsonArray pages = new JsonArray();
        for (ResourceLocation[] page : this.wheel) {
            JsonArray slots = new JsonArray();
            for (ResourceLocation slot : page) {
                if (slot == null) {
                    slots.add(JsonNull.INSTANCE);
                } else {
                    slots.add(slot.toString());
                }
            }

            pages.add(slots);
        }

        json.add("quickWheel", pages);
        try {
            Files.createDirectories(this.file.getParent());
            // write next to it and swap, so a crash while saving cannot leave a broken file behind
            Path temp = this.file.resolveSibling(this.file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp)) {
                GSON.toJson(json, writer);
            }

            try {
                Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, this.file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            PlayerEmotes.LOGGER.error("Failed to save {}", this.file, e);
        }
    }

    private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }

    private static void readSet(JsonObject json, String key, Set<String> target) {
        if (!json.has(key)) {
            return;
        }

        target.clear();
        for (JsonElement element : json.getAsJsonArray(key)) {
            target.add(element.getAsString());
        }
    }

    private static JsonArray writeSet(Set<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
