package com.yamikhal.playeremotes.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yamikhal.playeremotes.PlayerEmotes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// server rules in config/playeremotes-server.json, written with defaults on first start and re-read with
// /playeremotes reload. emote patterns are full ids (playeremotes:wave), whole namespaces (somepack:*) or bare
// names matching any namespace (wave)
public final class ServerConfig {

    private static final String FILE = PlayerEmotes.MOD_ID + "-server.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static volatile ServerConfig current;

    // when off, every emote is refused
    public final boolean enabled;
    // fewest ticks between two emotes of one player
    public final int cooldownTicks;
    // emotes nobody may play
    public final List<String> disabledEmotes;
    // emotes that need the playeremotes.restricted permission
    public final List<String> restrictedEmotes;
    // whether players may join others' emotes with /emotesync
    public final boolean syncEmotes;
    public final boolean partnerEmotes;
    // seconds a partner request waits for an answer before anyone may join instead
    public final int partnerRequestSeconds;
    // whether emote packs in data packs are sent to players
    public final boolean sendServerPacks;

    private ServerConfig(JsonObject json) {
        this.enabled = GsonHelper.getAsBoolean(json, "enabled", true);
        this.cooldownTicks = Math.max(0, Math.min(1200, GsonHelper.getAsInt(json, "cooldownTicks", 4)));
        this.disabledEmotes = patterns(json, "disabledEmotes");
        this.restrictedEmotes = patterns(json, "restrictedEmotes");
        this.syncEmotes = GsonHelper.getAsBoolean(json, "syncEmotes", true);
        this.partnerEmotes = GsonHelper.getAsBoolean(json, "partnerEmotes", true);
        this.partnerRequestSeconds = Math.max(5, Math.min(600, GsonHelper.getAsInt(json, "partnerRequestSeconds", 60)));
        this.sendServerPacks = GsonHelper.getAsBoolean(json, "sendServerPacks", true);
    }

    public boolean isDisabled(ResourceLocation emote) {
        return matches(this.disabledEmotes, emote);
    }

    public boolean isRestricted(ResourceLocation emote) {
        return matches(this.restrictedEmotes, emote);
    }

    public static ServerConfig get() {
        ServerConfig config = current;
        if (config == null) {
            config = reload();
        }

        return config;
    }

    // re-reads the file, writing it (with any missing keys) back so it documents every option
    public static synchronized ServerConfig reload() {
        Path file = PlayerEmotes.platform().configDir().resolve(FILE);
        JsonObject json = new JsonObject();
        boolean readable = true;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                // keep the broken file so its author can fix it
                readable = false;
                PlayerEmotes.LOGGER.error("Failed to read {}, using defaults until it is fixed", file, e);
            }
        }

        ServerConfig config = new ServerConfig(json);
        if (readable) {
            config.write(file);
        }

        current = config;
        return config;
    }

    private void write(Path file) {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", this.enabled);
        json.addProperty("cooldownTicks", this.cooldownTicks);
        json.add("disabledEmotes", array(this.disabledEmotes));
        json.add("restrictedEmotes", array(this.restrictedEmotes));
        json.addProperty("syncEmotes", this.syncEmotes);
        json.addProperty("partnerEmotes", this.partnerEmotes);
        json.addProperty("partnerRequestSeconds", this.partnerRequestSeconds);
        json.addProperty("sendServerPacks", this.sendServerPacks);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            PlayerEmotes.LOGGER.error("Failed to write {}", file, e);
        }
    }

    private static boolean matches(List<String> patterns, ResourceLocation emote) {
        for (String pattern : patterns) {
            if (pattern.endsWith(":*")) {
                if (emote.getNamespace().equals(pattern.substring(0, pattern.length() - 2))) {
                    return true;
                }
            } else if (pattern.indexOf(':') < 0) {
                if (emote.getPath().equals(pattern)) {
                    return true;
                }
            } else if (emote.toString().equals(pattern)) {
                return true;
            }
        }

        return false;
    }

    private static List<String> patterns(JsonObject json, String key) {
        List<String> patterns = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                patterns.add(element.getAsString().trim().toLowerCase(Locale.ROOT));
            }
        }

        return List.copyOf(patterns);
    }

    private static JsonArray array(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
