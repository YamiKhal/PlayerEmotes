package com.yamikhal.playeremotes.client.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.anim.AnimationParser;
import com.yamikhal.playeremotes.anim.EmoteAnimation;
import com.yamikhal.playeremotes.anim.Part;
import com.yamikhal.playeremotes.client.animation.AnimationRegistry;
import com.yamikhal.playeremotes.network.AnimatedProp;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.network.EmoteProp;
import com.yamikhal.playeremotes.network.EmoteSound;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

// loads emote packs from assets/<namespace>/playeremotes/:
//
// playeremotes/
//   pack.json              optional, describes the default pack
//   wave.json              Blockbench animation files directly in here belong to the default pack
//   dances/                every sub-folder is a pack
//     pack.json            optional
//     robot_dance.json     animation files, may be nested further
//
// every animation becomes an emote with default settings, unless pack.json configures it or uses it as variant of
// another emote:
//
// {
//   "name": "Dances", "description": "...", "author": "Someone", "version": "1.0.0",
//   "emotes": {
//     "clap": {
//       "animations": ["clap", "clap2"],  // default: the animation with the emote's name
//       "pick": "cycle",                  // or "random"
//       "look": true,                     // head follows the camera
//       "bend_style": "smooth",           // bent elbows and knees: "smooth" or "split" (two rigid halves)
//       "preview": 0.5,                   // seconds into the animation shown as the menu pose (default: middle)
//       "sound": "mymod:emote.clap",      // or {"id": ..., "volume": 1, "pitch": 1, "range": 8}
//       "item": "minecraft:cake",         // held while playing, or {"id": ..., "hand": "right|left|both"}
//       "author": "Someone",
//       "blend_in": 0.15, "blend_out": 0.2,   // seconds
//       "order": 0                        // lower comes first, then alphabetical
//     }
//   }
// }
//
// emote names and descriptions from lang keys emote.<namespace>.<name> and emote.<namespace>.<name>.description,
// pack names from emote_pack.<pack>
public final class EmoteRegistry {

    public static final String DIRECTORY = "playeremotes";
    public static final String DEFAULT_PACK = "default";
    private static final String PACK_FILE = "pack.json";
    private static final Map<ResourceLocation, Integer> CYCLE = new HashMap<>();

    private static Map<ResourceLocation, Emote> emotes = Map.of();
    private static List<EmotePack> packs = List.of();

    private EmoteRegistry() {}

    @Nullable
    public static Emote get(ResourceLocation id) {
        return emotes.get(id);
    }

    // all packs, sorted by name
    public static List<EmotePack> packs() {
        return packs;
    }

    // all emotes, grouped by pack in menu order
    public static List<Emote> all() {
        List<Emote> all = new ArrayList<>();
        for (EmotePack pack : packs) {
            all.addAll(pack.emotes());
        }

        return all;
    }

    // picks the animation to play right now, null if none available
    @Nullable
    public static ResourceLocation pickAnimation(Emote emote, Minecraft minecraft) {
        List<ResourceLocation> group = emote.animations();
        if (emote.selector() != null) {
            String variant = EmoteSelectors.select(emote.selector(), minecraft);
            if (variant != null && emote.variants().containsKey(variant)) {
                group = emote.variants().get(variant);
            }
        }

        List<ResourceLocation> available = group.stream().filter(id -> AnimationRegistry.get(id) != null).toList();
        if (available.isEmpty()) {
            return null;
        }

        if (available.size() == 1) {
            return available.get(0);
        }

        if (emote.pick() == Emote.Pick.RANDOM) {
            return available.get(ThreadLocalRandom.current().nextInt(available.size()));
        }

        int index = CYCLE.merge(emote.id(), 1, Integer::sum) - 1;
        return available.get(Math.floorMod(index, available.size()));
    }

    public static void reload(ResourceManager manager) {
        Map<ResourceLocation, EmoteAnimation> animations = new HashMap<>();
        // sorted by namespace, then pack, so merging is deterministic
        Map<String, Source> sources = new TreeMap<>();

        Map<ResourceLocation, FileSource> files = new TreeMap<>();
        manager.listResources(DIRECTORY, path -> path.getPath().endsWith(".json"))
                .forEach((id, resource) -> files.put(id, resource::openAsReader));
        // server packs come last, they win over local files with the same name
        for (Map.Entry<String, byte[]> file : ServerPackClient.files().entrySet()) {
            int slash = file.getKey().indexOf('/');
            ResourceLocation id = ResourceLocation.tryParse(file.getKey().substring(0, slash) + ":" + DIRECTORY + file.getKey().substring(slash));
            byte[] data = file.getValue();
            if (id != null) {
                files.put(id, () -> new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));
            }
        }

        for (Map.Entry<ResourceLocation, FileSource> file : files.entrySet()) {
            ResourceLocation fileId = file.getKey();
            String relative = fileId.getPath().substring(DIRECTORY.length() + 1);
            int slash = relative.indexOf('/');
            String pack = slash < 0 ? DEFAULT_PACK : relative.substring(0, slash);
            String fileName = relative.substring(slash + 1);
            Source source = sources.computeIfAbsent(fileId.getNamespace() + ":" + pack,
                    key -> new Source(fileId.getNamespace(), pack));

            try (Reader reader = file.getValue().open()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (fileName.equals(PACK_FILE)) {
                    source.info = json;
                    continue;
                }

                for (Map.Entry<String, EmoteAnimation> entry : AnimationParser.parse(json).entrySet()) {
                    ResourceLocation id = PlayerEmotes.id(fileId.getNamespace(), entry.getKey());
                    if (animations.put(id, entry.getValue()) != null) {
                        PlayerEmotes.LOGGER.warn("Animation {} is defined more than once (last one in {} wins)", id, fileId);
                    }

                    source.animations.add(entry.getKey());
                }
            } catch (Exception e) {
                PlayerEmotes.LOGGER.error("Failed to load {}: {}", fileId, e.getMessage());
            }
        }

        AnimationRegistry.set(animations);

        Map<ResourceLocation, Emote> loaded = new LinkedHashMap<>();
        Map<String, List<Source>> byPack = new LinkedHashMap<>();
        for (Source source : sources.values()) {
            List<Emote> read;
            try {
                read = readEmotes(source);
            } catch (RuntimeException e) {
                // broken pack.json must not fail the whole resource reload
                PlayerEmotes.LOGGER.error("Failed to load emote pack '{}' of {}: {}", source.pack, source.namespace, e.getMessage());
                continue;
            }

            byPack.computeIfAbsent(source.pack, key -> new ArrayList<>()).add(source);
            for (Emote emote : read) {
                if (loaded.put(emote.id(), emote) != null) {
                    PlayerEmotes.LOGGER.warn("Emote {} is defined in more than one pack, the one in '{}' wins", emote.id(), source.pack);
                }

                warnUnusedProps(emote);
            }
        }

        List<EmotePack> result = new ArrayList<>();
        for (Map.Entry<String, List<Source>> pack : byPack.entrySet()) {
            List<Emote> members = loaded.values().stream().filter(emote -> emote.pack().equals(pack.getKey())).toList();
            if (!members.isEmpty()) {
                result.add(readPack(pack.getKey(), pack.getValue(), members));
            }
        }

        result.sort(Comparator.comparing(pack -> pack.name().getString().toLowerCase(Locale.ROOT)));

        emotes = Map.copyOf(loaded);
        packs = List.copyOf(result);
        PlayerEmotes.LOGGER.info("Loaded {} emotes in {} packs", emotes.size(), packs.size());
    }

    private static EmotePack readPack(String id, List<Source> sources, List<Emote> members) {
        String name = null;
        String description = "";
        String author = "";
        String version = "";
        for (Source source : sources) {
            if (source.info == null) continue;

            // first namespace describing the pack wins, but our own wins for the default pack
            boolean own = source.namespace.equals(PlayerEmotes.MOD_ID);
            if (name == null || own) {
                try {
                    name = GsonHelper.getAsString(source.info, "name", name);
                    description = GsonHelper.getAsString(source.info, "description", description);
                    author = readAuthor(source.info, author);
                    version = GsonHelper.getAsString(source.info, "version", version);
                } catch (RuntimeException e) {
                    PlayerEmotes.LOGGER.error("Invalid info in pack.json of emote pack '{}' of {}: {}", id, source.namespace, e.getMessage());
                }
            }
        }

        String fallbackName = name != null ? name : id.equals(DEFAULT_PACK) ? "Default" : prettify(id);
        List<Emote> sorted = new ArrayList<>(members);
        sorted.sort(Comparator.comparingInt(Emote::order)
                .thenComparing(emote -> emote.name().getString().toLowerCase(Locale.ROOT)));
        return new EmotePack(id,
                Component.translatableWithFallback("emote_pack." + id, fallbackName),
                Component.translatableWithFallback("emote_pack." + id + ".description", description),
                author, version, List.copyOf(sorted));
    }

    private static String readAuthor(JsonObject json, String fallback) {
        JsonElement author = json.get(json.has("authors") ? "authors" : "author");
        if (author == null) {
            return fallback;
        }

        if (!author.isJsonArray()) {
            return author.getAsString();
        }

        List<String> names = new ArrayList<>();
        for (JsonElement element : author.getAsJsonArray()) {
            names.add(element.getAsString());
        }

        return String.join(", ", names);
    }

    private static List<Emote> readEmotes(Source source) {
        List<Emote> result = new ArrayList<>();
        JsonObject configured = source.info != null && source.info.has("emotes") ? source.info.getAsJsonObject("emotes") : new JsonObject();
        Set<ResourceLocation> used = new HashSet<>();
        for (Map.Entry<String, JsonElement> entry : configured.entrySet()) {
            try {
                ResourceLocation id = PlayerEmotes.id(source.namespace, entry.getKey());
                Emote emote = read(id, source.pack, entry.getValue().getAsJsonObject());
                used.addAll(emote.animations());
                emote.variants().values().forEach(used::addAll);
                if (emote.partner() != null) {
                    used.add(emote.partner().intro());
                    used.add(emote.partner().action());
                    used.add(emote.partner().partnerAction());
                }

                result.add(emote);
            } catch (RuntimeException e) {
                PlayerEmotes.LOGGER.error("Failed to load emote {}:{} of pack '{}': {}", source.namespace, entry.getKey(), source.pack, e.getMessage());
            }
        }

        // animations not configured and not part of another emote become emotes on their own
        for (String animation : source.animations) {
            ResourceLocation id = PlayerEmotes.id(source.namespace, animation);
            if (configured.has(animation) || used.contains(id)) continue;

            result.add(read(id, source.pack, new JsonObject()));
        }

        return result;
    }

    private static Emote read(ResourceLocation id, String pack, JsonObject json) {
        String namespace = id.getNamespace();
        String langKey = "emote." + namespace + "." + id.getPath().replace('/', '.');
        String fallbackName = GsonHelper.getAsString(json, "name", prettify(id.getPath()));
        String fallbackDescription = GsonHelper.getAsString(json, "description", "");
        Component name = Component.translatableWithFallback(langKey, fallbackName);
        Component description = Component.translatableWithFallback(langKey + ".description", fallbackDescription);

        List<ResourceLocation> animations = json.has("animations")
                ? ids(json.getAsJsonArray("animations"), namespace)
                : List.of(PlayerEmotes.id(namespace, GsonHelper.getAsString(json, "animation", id.getPath())));

        Map<String, List<ResourceLocation>> variants = new HashMap<>();
        if (json.has("variants")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("variants").entrySet()) {
                JsonElement value = entry.getValue();
                variants.put(entry.getKey(), value.isJsonArray()
                        ? ids(value.getAsJsonArray(), namespace)
                        : List.of(resolve(value.getAsString(), namespace)));
            }
        }

        List<ResourceLocation> defaults = !json.has("animations") && !variants.isEmpty()
                ? variants.values().iterator().next()
                : animations;

        String pickName = GsonHelper.getAsString(json, "pick", "cycle");
        Emote.Pick pick = pickName.equalsIgnoreCase("random") ? Emote.Pick.RANDOM : Emote.Pick.CYCLE;

        return new Emote(id, pack, name, description,
                GsonHelper.getAsString(json, "author", ""),
                GsonHelper.getAsDouble(json, "preview", -1), List.copyOf(defaults), Map.copyOf(variants),
                json.has("selector") ? GsonHelper.getAsString(json, "selector") : null,
                pick,
                GsonHelper.getAsBoolean(json, "look", true),
                GsonHelper.getAsString(json, "bend_style", "smooth").equalsIgnoreCase("split"),
                seconds(json, "blend_in", 0.15F),
                seconds(json, "blend_out", 0.2F),
                readSound(json.get("sound"), namespace),
                readProp(json.get("item"), namespace),
                readProps(json.get("props")),
                readPartner(json.get("partner"), namespace, defaults),
                GsonHelper.getAsInt(json, "order", 0));
    }

    // "partner": {"intro": "hug_intro", "action": "hug", "partner_action": "hug", "distance": 0.6}
    // intro plays (should hold its last frame) until someone joins, then starter plays action and partner
    // partner_action (default same), distance blocks apart
    @Nullable
    private static EmoteNetwork.PartnerSpec readPartner(@Nullable JsonElement json, String namespace, List<ResourceLocation> defaults) {
        if (json == null || !json.isJsonObject()) {
            return null;
        }

        JsonObject object = json.getAsJsonObject();
        ResourceLocation intro = resolve(GsonHelper.getAsString(object, "intro"), namespace);
        ResourceLocation action = object.has("action") ? resolve(GsonHelper.getAsString(object, "action"), namespace)
                : defaults.isEmpty() ? intro : defaults.get(0);
        ResourceLocation partnerAction = object.has("partner_action")
                ? resolve(GsonHelper.getAsString(object, "partner_action"), namespace) : action;
        return new EmoteNetwork.PartnerSpec(intro, action, partnerAction, GsonHelper.getAsFloat(object, "distance", 1));
    }

    // "item": "minecraft:cake" or {"id": "minecraft:cake", "hand": "left"} (right, left or both)
    @Nullable
    private static EmoteProp readProp(@Nullable JsonElement json, String namespace) {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        if (json.isJsonPrimitive()) {
            return new EmoteProp(resolveItem(json.getAsString()), EmoteProp.Hand.RIGHT);
        }

        JsonObject object = json.getAsJsonObject();
        EmoteProp.Hand hand = switch (GsonHelper.getAsString(object, "hand", "right").toLowerCase(Locale.ROOT)) {
            case "left" -> EmoteProp.Hand.LEFT;
            case "both" -> EmoteProp.Hand.BOTH;
            default -> EmoteProp.Hand.RIGHT;
        };
        return new EmoteProp(resolveItem(GsonHelper.getAsString(object, "id")), hand);
    }

    // "props": {"<bone>": "minecraft:stick"} or {"<bone>": {"item": "held", "attach": "right_hand", "display": "hand"}}
    // item is an id, none (only carries other props) or held (the hand it is attached to, else the main hand),
    // held_right, held_left, held_mainhand, held_offhand, default held. attach defaults from the bone name (right, left,
    // head, body or torso), else root. optional "parent" and "pivot" nest props, see readNestedProps
    private static List<AnimatedProp> readProps(@Nullable JsonElement json) {
        if (json == null || !json.isJsonObject()) {
            return List.of();
        }

        List<Map.Entry<String, JsonObject>> entries = new ArrayList<>();
        boolean nested = false;
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
            String bone = entry.getKey().toLowerCase(Locale.ROOT);
            if (bone.isEmpty() || bone.length() > AnimatedProp.MAX_BONE_LENGTH) {
                throw new IllegalArgumentException("Invalid prop bone name '" + entry.getKey() + "'");
            }

            // player bones never reach the props, such a prop would never move
            if (Part.byBoneName(bone) != null) {
                throw new IllegalArgumentException("Prop bone '" + entry.getKey() + "' is a player bone");
            }

            if (entries.size() >= AnimatedProp.MAX_PROPS) {
                throw new IllegalArgumentException("More than " + AnimatedProp.MAX_PROPS + " props");
            }

            JsonObject object;
            if (entry.getValue().isJsonObject()) {
                object = entry.getValue().getAsJsonObject();
            } else if (entry.getValue().isJsonPrimitive()) {
                object = new JsonObject();
                object.add("item", entry.getValue());
            } else {
                throw new IllegalArgumentException("Prop '" + entry.getKey() + "' must be an item or an object");
            }

            nested |= object.has("parent") || object.has("pivot");
            entries.add(Map.entry(bone, object));
        }

        if (nested) {
            return readNestedProps(entries);
        }

        List<AnimatedProp> props = new ArrayList<>(entries.size());
        for (Map.Entry<String, JsonObject> entry : entries) {
            props.add(readProp(entry.getKey(), entry.getValue(), propAttach(entry.getKey(), entry.getValue()), -1, Vec3.ZERO));
        }

        return List.copyOf(props);
    }

    // only for packs that use them: "parent": "<bone>" hangs a prop on another one and takes its attach, "pivot" is
    // the bone's Blockbench pivot, default the parent's or the attach point's. a top prop rests on it, a nested one
    // only turns around it like in Blockbench. parents go first in the list
    private static List<AnimatedProp> readNestedProps(List<Map.Entry<String, JsonObject>> entries) {
        // bone names ignore case, so two keys can be the same bone
        Map<String, JsonObject> objects = new LinkedHashMap<>();
        for (Map.Entry<String, JsonObject> entry : entries) {
            if (objects.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalArgumentException("Prop bone '" + entry.getKey() + "' is there twice");
            }
        }

        List<AnimatedProp> props = new ArrayList<>();
        // Blockbench pivots, and where the top prop of each chain rests
        List<Vec3> pivots = new ArrayList<>();
        List<Vec3> rests = new ArrayList<>();
        Map<String, Integer> indices = new HashMap<>();
        while (props.size() < objects.size()) {
            int placed = props.size();
            for (Map.Entry<String, JsonObject> entry : objects.entrySet()) {
                String bone = entry.getKey();
                JsonObject object = entry.getValue();
                String parentBone = object.has("parent") ? GsonHelper.getAsString(object, "parent").toLowerCase(Locale.ROOT) : null;
                if (parentBone != null && !objects.containsKey(parentBone)) {
                    throw new IllegalArgumentException("Parent '" + parentBone + "' of prop '" + bone + "' is not in props");
                }

                if (indices.containsKey(bone) || (parentBone != null && !indices.containsKey(parentBone))) {
                    continue;
                }

                if (parentBone != null && object.has("attach")) {
                    throw new IllegalArgumentException("Prop '" + bone + "' has a parent, it takes the parent's attach");
                }

                int parent = parentBone == null ? -1 : indices.get(parentBone);
                AnimatedProp.Attach attach = parent >= 0 ? props.get(parent).attach() : propAttach(bone, object);
                Vec3 pivot = object.has("pivot") ? readPivot(object, bone) : parent >= 0 ? pivots.get(parent) : attachPivot(attach);
                // a top prop rests on its pivot, a nested one turns around it
                Vec3 rest = parent >= 0 ? rests.get(parent) : pivot;
                props.add(readProp(bone, object, attach, parent, pivot.subtract(parent >= 0 ? rest : attachPivot(attach))));
                pivots.add(pivot);
                rests.add(rest);
                indices.put(bone, props.size() - 1);
            }

            if (props.size() == placed) {
                throw new IllegalArgumentException("Props " + objects.keySet() + " are parents of each other in a loop");
            }
        }

        return List.copyOf(props);
    }

    private static AnimatedProp readProp(String bone, JsonObject object, AnimatedProp.Attach attach, int parent, Vec3 pivot) {
        AnimatedProp.Display display = object.has("display") ? display(GsonHelper.getAsString(object, "display"))
                : AnimatedProp.Display.AUTO;
        String item = GsonHelper.getAsString(object, "item", "held");
        AnimatedProp.Source source = switch (item.trim().toLowerCase(Locale.ROOT)) {
            case "held" -> attach == AnimatedProp.Attach.RIGHT_HAND ? AnimatedProp.Source.HELD_RIGHT
                    : attach == AnimatedProp.Attach.LEFT_HAND ? AnimatedProp.Source.HELD_LEFT
                    : AnimatedProp.Source.HELD_MAINHAND;
            case "held_right" -> AnimatedProp.Source.HELD_RIGHT;
            case "held_left" -> AnimatedProp.Source.HELD_LEFT;
            case "held_mainhand" -> AnimatedProp.Source.HELD_MAINHAND;
            case "held_offhand" -> AnimatedProp.Source.HELD_OFFHAND;
            case "none" -> AnimatedProp.Source.NONE;
            default -> AnimatedProp.Source.ITEM;
        };
        return new AnimatedProp(bone, source, source == AnimatedProp.Source.ITEM ? resolveItem(item) : null, attach, display,
                parent, pivot);
    }

    private static AnimatedProp.Attach propAttach(String bone, JsonObject object) {
        return object.has("attach") ? attach(GsonHelper.getAsString(object, "attach"))
                : bone.contains("right") ? AnimatedProp.Attach.RIGHT_HAND
                : bone.contains("left") ? AnimatedProp.Attach.LEFT_HAND
                : bone.contains("head") ? AnimatedProp.Attach.HEAD
                : bone.contains("body") || bone.contains("torso") ? AnimatedProp.Attach.BODY
                : AnimatedProp.Attach.ROOT;
    }

    // Blockbench pivot of the attach point's bone in the templates, where a prop without a pivot rests
    private static Vec3 attachPivot(AnimatedProp.Attach attach) {
        return switch (attach) {
            case RIGHT_HAND -> new Vec3(-6, 12, -2);
            case LEFT_HAND -> new Vec3(6, 12, -2);
            case BODY -> new Vec3(0, 18, 0);
            case HEAD -> new Vec3(0, 28, 0);
            case ROOT -> new Vec3(0, 8, 0);
        };
    }

    // "pivot": [x, y, z] like Blockbench shows it
    private static Vec3 readPivot(JsonObject object, String bone) {
        JsonArray array = GsonHelper.getAsJsonArray(object, "pivot");
        Vec3 pivot = array.size() == 3
                ? new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble())
                : null;
        if (pivot == null || !Double.isFinite(pivot.x) || !Double.isFinite(pivot.y) || !Double.isFinite(pivot.z)) {
            throw new IllegalArgumentException("Pivot of prop '" + bone + "' must be [x, y, z]");
        }

        return pivot;
    }

    // prop whose bone no animation of the emote keys only ever sits at rest, mostly a typo in the bone name
    private static void warnUnusedProps(Emote emote) {
        if (emote.props().isEmpty()) {
            return;
        }

        List<ResourceLocation> ids = new ArrayList<>(emote.animations());
        emote.variants().values().forEach(ids::addAll);
        if (emote.partner() != null) {
            ids.addAll(List.of(emote.partner().intro(), emote.partner().action(), emote.partner().partnerAction()));
        }

        for (AnimatedProp prop : emote.props()) {
            boolean keyed = ids.stream()
                    .map(AnimationRegistry::get)
                    .anyMatch(animation -> animation != null && animation.props().containsKey(prop.bone()));
            if (!keyed) {
                PlayerEmotes.LOGGER.warn("Prop bone '{}' of emote {} is in none of its animations, it stays at rest", prop.bone(), emote.id());
            }
        }
    }

    private static AnimatedProp.Attach attach(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "right_hand" -> AnimatedProp.Attach.RIGHT_HAND;
            case "left_hand" -> AnimatedProp.Attach.LEFT_HAND;
            case "body", "torso" -> AnimatedProp.Attach.BODY;
            case "head" -> AnimatedProp.Attach.HEAD;
            case "root" -> AnimatedProp.Attach.ROOT;
            default -> throw new IllegalArgumentException("Unknown prop attach '" + name + "' (right_hand, left_hand, body, head, root)");
        };
    }

    private static AnimatedProp.Display display(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "hand" -> AnimatedProp.Display.HAND;
            case "none", "block" -> AnimatedProp.Display.NONE;
            case "fixed" -> AnimatedProp.Display.FIXED;
            case "ground" -> AnimatedProp.Display.GROUND;
            case "head" -> AnimatedProp.Display.HEAD;
            default -> throw new IllegalArgumentException("Unknown prop display '" + name + "' (hand, none, fixed, ground, head)");
        };
    }

    // item ids default to the minecraft namespace, like everywhere in the game
    private static ResourceLocation resolveItem(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value.trim().toLowerCase(Locale.ROOT));
        if (id == null) {
            throw new IllegalArgumentException("Invalid item id '" + value + "'");
        }

        return id;
    }

    @Nullable
    private static EmoteSound readSound(@Nullable JsonElement json, String namespace) {
        if (json == null || json.isJsonNull()) {
            return null;
        }

        if (json.isJsonPrimitive()) {
            return new EmoteSound(resolve(json.getAsString(), namespace), 1, 1, EmoteSound.DEFAULT_RANGE);
        }

        JsonObject object = json.getAsJsonObject();
        return new EmoteSound(resolve(GsonHelper.getAsString(object, "id"), namespace),
                GsonHelper.getAsFloat(object, "volume", 1),
                GsonHelper.getAsFloat(object, "pitch", 1),
                GsonHelper.getAsFloat(object, "range", EmoteSound.DEFAULT_RANGE));
    }

    private static int seconds(JsonObject json, String key, float fallback) {
        return Math.round(GsonHelper.getAsFloat(json, key, fallback) * 20);
    }

    private static List<ResourceLocation> ids(JsonArray array, String namespace) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (JsonElement element : array) {
            ids.add(resolve(element.getAsString(), namespace));
        }

        return List.copyOf(ids);
    }

    private static ResourceLocation resolve(String value, String namespace) {
        ResourceLocation id = value.indexOf(':') < 0 ? PlayerEmotes.id(namespace, value) : ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid id '" + value + "'");
        }

        return id;
    }

    private static String prettify(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder builder = new StringBuilder();
        for (String word : name.split("[_-]")) {
            if (word.isEmpty()) continue;

            if (!builder.isEmpty()) {
                builder.append(' ');
            }

            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        return builder.toString();
    }

    // opens one emote file, from a resource pack or the server
    private interface FileSource {

        Reader open() throws IOException;
    }

    // one pack folder in one namespace
    private static final class Source {

        final String namespace;
        final String pack;
        @Nullable
        JsonObject info;
        // animation names in file order
        final List<String> animations = new ArrayList<>();

        Source(String namespace, String pack) {
            this.namespace = namespace;
            this.pack = pack;
        }
    }
}
