package com.yamikhal.playeremotes.client.emote;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.anim.AnimationParser;
import com.yamikhal.playeremotes.anim.EmoteAnimation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// AzureLib animation includes, a file pulls animations out of other files:
//
// "includes": [
//   {"file_id": "mymod:animations/creature.animation.json", "animations": ["animation.creature.wave"]}
// ]
//
// file_id is a path in assets like AzureLib (no namespace means the including file's), so animations of any mod or
// resource pack can be used without copying them, and only the listed ones become emotes (no list takes all). the
// file's own animations win, then includes in order. included files may include further, cycles and deep chains are
// refused. each file read and parsed once per reload
final class AnimationIncludes {

    // deeper chains are a mistake or a server pack making work
    private static final int MAX_DEPTH = 8;

    private final Map<ResourceLocation, EmoteRegistry.FileSource> files;
    private final ResourceManager manager;
    // files of the emote folders, already parsed by the registry: own animations and includes
    private final Map<ResourceLocation, Map<String, EmoteAnimation>> known = new HashMap<>();
    private final Map<ResourceLocation, JsonElement> knownIncludes = new HashMap<>();
    // own animations plus everything included, by file
    private final Map<ResourceLocation, Map<String, EmoteAnimation>> resolved = new HashMap<>();
    private final Set<ResourceLocation> visiting = new HashSet<>();

    AnimationIncludes(Map<ResourceLocation, EmoteRegistry.FileSource> files, ResourceManager manager) {
        this.files = files;
        this.manager = manager;
    }

    // a file the registry parsed, so it isn't read again when included
    void remember(ResourceLocation file, Map<String, EmoteAnimation> animations, @Nullable JsonElement includes) {
        this.known.put(file, animations);
        if (includes != null) {
            this.knownIncludes.put(file, includes);
        }
    }

    // animations the file gets from its includes, without its own
    Map<String, EmoteAnimation> included(ResourceLocation file) {
        Map<String, EmoteAnimation> all = new LinkedHashMap<>(this.resolve(file, file, 0));
        all.keySet().removeAll(this.known.getOrDefault(file, Map.of()).keySet());
        return all;
    }

    private Map<String, EmoteAnimation> resolve(ResourceLocation file, ResourceLocation root, int depth) {
        Map<String, EmoteAnimation> done = this.resolved.get(file);
        if (done != null) {
            return done;
        }

        Map<String, EmoteAnimation> own = this.known.get(file);
        JsonElement includes = this.knownIncludes.get(file);
        if (own == null) {
            JsonObject json = this.read(file);
            if (json == null) {
                PlayerEmotes.LOGGER.warn("{} includes {}, which doesn't exist or isn't readable", root, file);
                return Map.of();
            }

            List<String> errors = new ArrayList<>();
            own = AnimationParser.parse(json, errors);
            errors.forEach(error -> PlayerEmotes.LOGGER.warn("{}: {}", file, error));
            includes = json.get("includes");
        }

        Map<String, EmoteAnimation> result = new LinkedHashMap<>(own);
        if (includes != null && includes.isJsonArray()) {
            this.visiting.add(file);
            for (JsonElement entry : includes.getAsJsonArray()) {
                this.include(file, entry, result, root, depth);
            }

            this.visiting.remove(file);
        }

        this.resolved.put(file, result);
        return result;
    }

    private void include(ResourceLocation file, JsonElement entry, Map<String, EmoteAnimation> result, ResourceLocation root, int depth) {
        if (!entry.isJsonObject() || !entry.getAsJsonObject().has("file_id")) {
            PlayerEmotes.LOGGER.warn("{}: include without \"file_id\"", file);
            return;
        }

        JsonObject object = entry.getAsJsonObject();
        ResourceLocation target = target(object.get("file_id").getAsString(), file.getNamespace());
        if (target == null) {
            PlayerEmotes.LOGGER.warn("{}: invalid include file_id '{}'", file, object.get("file_id").getAsString());
            return;
        }

        if (this.visiting.contains(target)) {
            PlayerEmotes.LOGGER.warn("{}: include of {} goes in a circle, skipped", file, target);
            return;
        }

        if (depth >= MAX_DEPTH) {
            PlayerEmotes.LOGGER.warn("{}: includes nested deeper than {}, {} skipped", root, MAX_DEPTH, target);
            return;
        }

        Map<String, EmoteAnimation> available = this.resolve(target, root, depth + 1);
        JsonElement wanted = object.get("animations");
        if (wanted == null || !wanted.isJsonArray()) {
            available.forEach(result::putIfAbsent);
            return;
        }

        for (JsonElement name : wanted.getAsJsonArray()) {
            String key = AnimationParser.animationName(name.getAsString());
            EmoteAnimation animation = available.get(key);
            if (animation == null) {
                PlayerEmotes.LOGGER.warn("{}: included animation '{}' not found in {}", file, name.getAsString(), target);
            } else {
                result.putIfAbsent(key, animation);
            }
        }
    }

    @Nullable
    private static ResourceLocation target(String fileId, String namespace) {
        String id = fileId.trim();
        if (!id.endsWith(".json")) {
            id += ".json";
        }

        return ResourceLocation.tryParse(id.indexOf(':') < 0 ? namespace + ":" + id : id);
    }

    // emote folder files (server packs included) first, then any resource
    @Nullable
    private JsonObject read(ResourceLocation file) {
        try {
            EmoteRegistry.FileSource source = this.files.get(file);
            if (source != null) {
                try (Reader reader = source.open()) {
                    return JsonParser.parseReader(reader).getAsJsonObject();
                }
            }

            Optional<Resource> resource = this.manager.getResource(file);
            if (resource.isEmpty()) {
                return null;
            }

            try (Reader reader = resource.get().openAsReader()) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (Exception e) {
            PlayerEmotes.LOGGER.warn("Failed to read included file {}: {}", file, e.getMessage());
            return null;
        }
    }
}
