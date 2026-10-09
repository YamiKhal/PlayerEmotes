package com.yamikhal.playeremotes.anim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// reads Bedrock animation files from Blockbench (*.animation.json): animation_length, loop (true, false,
// "hold_on_last_frame"), rotation/position as constants or keyframes, pre/post, lerp_mode (linear, catmullrom,
// step), GeckoLib / AzureLib easing/easingArgs, Molang values and sound_effects / particle_effects keyframes. bones
// outside the skeleton are kept as prop bones (rotation, position and scale), scale of skeleton bones is skipped.
// AzureLib "timeline" instruction keyframes only mean something to the mod that made them, skipped
public final class AnimationParser {

    private static final Molang.Expr ZERO = Molang.constant(0);
    // helper bones of big rigs are no props, they only cost memory
    private static final int MAX_PROP_BONES = 32;

    private AnimationParser() {}

    // animations by lower case name, throws if one is broken
    public static Map<String, EmoteAnimation> parse(JsonObject root) {
        return parse(root, null);
    }

    // animations by lower case name. with errors given, broken animations get skipped and their errors added, the
    // rest of the file still loads (AzureLib files hold all animations of a model, one bad one should not cost all).
    // a file with only "includes" (AzureLib) has no animations of its own
    public static Map<String, EmoteAnimation> parse(JsonObject root, @Nullable List<String> errors) {
        JsonElement animations = root.get("animations");
        if (animations == null && root.has("includes")) {
            return new LinkedHashMap<>();
        }

        if (animations == null || !animations.isJsonObject()) {
            throw new JsonParseException("Not a Bedrock animation file (missing \"animations\")");
        }

        Map<String, EmoteAnimation> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : animations.getAsJsonObject().entrySet()) {
            String name = animationName(entry.getKey());
            try {
                result.put(name, parseAnimation(name, entry.getValue().getAsJsonObject()));
            } catch (RuntimeException e) {
                String error = "Invalid animation '" + entry.getKey() + "': " + e.getMessage();
                if (errors == null) {
                    throw new JsonParseException(error, e);
                }

                errors.add(error);
            }
        }

        return result;
    }

    // file key to animation name: lower case, Blockbench's "animation.model.name" to its last segment, characters
    // ids can't have (AzureLib names like "Attack 1") to _
    public static String animationName(String key) {
        String name = key.toLowerCase(Locale.ROOT);
        if (name.startsWith("animation.")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }

        StringBuilder result = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            result.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
        }

        return result.toString();
    }

    private static EmoteAnimation parseAnimation(String name, JsonObject node) {
        Map<Part, EmoteAnimation.Bone> bones = new EnumMap<>(Part.class);
        Map<String, EmoteAnimation.PropBone> props = new HashMap<>();
        double lastKeyframe = 0;
        JsonElement bonesNode = node.get("bones");
        if (bonesNode != null) {
            // parts set by a rig alias (bipedHead), our own name (head) replaces them
            Set<Part> aliased = EnumSet.noneOf(Part.class);
            for (Map.Entry<String, JsonElement> bone : bonesNode.getAsJsonObject().entrySet()) {
                Part part = Part.byBoneName(bone.getKey());
                Channel[] channels;
                if (part != null) {
                    boolean alias = !Part.isOwnBoneName(bone.getKey());
                    if (bones.containsKey(part) && (alias || !aliased.contains(part))) continue;

                    JsonObject boneNode = bone.getValue().getAsJsonObject();
                    Channel rotation = readChannel(boneNode.get("rotation"));
                    Channel position = readChannel(boneNode.get("position"));
                    if (rotation == null && position == null) continue;

                    bones.put(part, new EmoteAnimation.Bone(rotation, position));
                    if (alias) {
                        aliased.add(part);
                    } else {
                        aliased.remove(part);
                    }

                    channels = new Channel[]{rotation, position};
                } else {
                    EmoteAnimation.PropBone prop = readPropBone(bone.getValue());
                    if (prop == null || props.size() >= MAX_PROP_BONES) continue;

                    props.put(bone.getKey().toLowerCase(Locale.ROOT), prop);
                    channels = new Channel[]{prop.rotation(), prop.position(), prop.scale()};
                }

                for (Channel channel : channels) {
                    if (channel != null) {
                        lastKeyframe = Math.max(lastKeyframe, channel.lastTime());
                    }
                }
            }
        }

        EmoteAnimation.LoopMode loop = EmoteAnimation.LoopMode.ONCE;
        JsonElement loopNode = node.get("loop");
        if (loopNode != null && loopNode.isJsonPrimitive()) {
            JsonPrimitive primitive = loopNode.getAsJsonPrimitive();
            if (primitive.isBoolean() && primitive.getAsBoolean()) {
                loop = EmoteAnimation.LoopMode.LOOP;
            } else if (primitive.isString() && primitive.getAsString().equals("hold_on_last_frame")) {
                loop = EmoteAnimation.LoopMode.HOLD;
            }
        }

        double length = node.has("animation_length") ? node.get("animation_length").getAsDouble() : lastKeyframe;
        if (!Double.isFinite(length) || length < 0) {
            length = lastKeyframe;
        }

        // zero length loop is a static pose, hold it instead
        if (loop == EmoteAnimation.LoopMode.LOOP && length <= 0) {
            loop = EmoteAnimation.LoopMode.HOLD;
        }

        List<EmoteAnimation.Effect> effects = new ArrayList<>();
        readEffects(node.get("sound_effects"), EmoteAnimation.Effect.Kind.SOUND, effects);
        readEffects(node.get("particle_effects"), EmoteAnimation.Effect.Kind.PARTICLE, effects);
        double end = length;
        effects.removeIf(effect -> effect.time() < 0 || (end > 0 && effect.time() > end));
        effects.sort(Comparator.comparingDouble(EmoteAnimation.Effect::time));
        return new EmoteAnimation(name, length, loop, bones, Map.copyOf(props), List.copyOf(effects));
    }

    // {"0.5": {"effect": "minecraft:heart", "locator": "head"}}, a time may also hold an array of effects
    private static void readEffects(JsonElement node, EmoteAnimation.Effect.Kind kind, List<EmoteAnimation.Effect> out) {
        if (node == null || !node.isJsonObject()) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : node.getAsJsonObject().entrySet()) {
            double time = time(entry.getKey());
            JsonElement value = entry.getValue();
            Iterable<JsonElement> list = value.isJsonArray() ? value.getAsJsonArray() : List.of(value);
            for (JsonElement element : list) {
                if (!element.isJsonObject()) continue;

                JsonObject object = element.getAsJsonObject();
                JsonElement effect = object.get("effect");
                if (effect == null || !effect.isJsonPrimitive() || effect.getAsString().isBlank()) continue;

                String locator = object.has("locator") ? object.get("locator").getAsString() : null;
                float volume = object.has("volume") ? object.get("volume").getAsFloat() : 1;
                float pitch = object.has("pitch") ? object.get("pitch").getAsFloat() : 1;
                out.add(new EmoteAnimation.Effect(time, kind, effect.getAsString().trim().toLowerCase(Locale.ROOT), locator,
                        Math.max(0, Math.min(1, volume)), Math.max(0.5F, Math.min(2, pitch))));
            }
        }
    }

    // null for bones without channels, or broken ones: other bones were skipped before props existed, a helper bone
    // of some rig must not break the animation now
    private static EmoteAnimation.PropBone readPropBone(JsonElement node) {
        try {
            JsonObject boneNode = node.getAsJsonObject();
            Channel rotation = readChannel(boneNode.get("rotation"));
            Channel position = readChannel(boneNode.get("position"));
            Channel scale = readChannel(boneNode.get("scale"));
            return rotation == null && position == null && scale == null ? null : new EmoteAnimation.PropBone(rotation, position, scale);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Channel readChannel(JsonElement node) {
        if (node == null) {
            return null;
        }

        List<Channel.Keyframe> frames = new ArrayList<>();
        if (node.isJsonArray() || node.isJsonPrimitive()) {
            Molang.Expr[] vector = vector(node);
            frames.add(new Channel.Keyframe(0, vector, vector, Channel.Lerp.LINEAR, null, Double.NaN));
        } else {
            JsonObject object = node.getAsJsonObject();
            if (object.has("vector")) {
                // GeckoLib single keyframe: { "vector": [...], "easing": ... }
                frames.add(keyframe(0, object));
            } else {
                for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                    double time = time(entry.getKey());
                    JsonElement value = entry.getValue();
                    if (value.isJsonObject()) {
                        frames.add(keyframe(time, value.getAsJsonObject()));
                    } else {
                        Molang.Expr[] vector = vector(value);
                        frames.add(new Channel.Keyframe(time, vector, vector, Channel.Lerp.LINEAR, null, Double.NaN));
                    }
                }
            }
        }

        return frames.isEmpty() ? null : new Channel(frames);
    }

    private static Channel.Keyframe keyframe(double time, JsonObject node) {
        Molang.Expr[] main = node.has("vector") ? vector(node.get("vector")) : null;
        Molang.Expr[] pre = node.has("pre") ? vector(unwrap(node.get("pre"))) : main;
        Molang.Expr[] post = node.has("post") ? vector(unwrap(node.get("post"))) : main;
        if (pre == null) {
            pre = post;
        }

        if (post == null) {
            post = pre;
        }

        if (pre == null) {
            throw new JsonParseException("keyframe at " + time + " has no value");
        }

        Channel.Lerp lerp = Channel.Lerp.LINEAR;
        if (node.has("lerp_mode")) {
            lerp = switch (node.get("lerp_mode").getAsString().toLowerCase(Locale.ROOT)) {
                case "catmullrom" -> Channel.Lerp.CATMULLROM;
                case "step" -> Channel.Lerp.STEP;
                default -> Channel.Lerp.LINEAR;
            };
        }

        Ease easing = null;
        double easingArg = Double.NaN;
        if (node.has("easing")) {
            String name = node.get("easing").getAsString();
            easing = Ease.byName(name);
            // GeckoLib and AzureLib also take the lerp modes as easing
            if (easing == null && name.equalsIgnoreCase("catmullrom")) {
                lerp = Channel.Lerp.CATMULLROM;
            } else if (easing == null) {
                throw new JsonParseException("unknown easing '" + name + "'");
            }

            if (easing == Ease.LINEAR) {
                easing = null;
            }
        }

        if (node.has("easingArgs")) {
            JsonElement args = node.get("easingArgs");
            JsonElement first = args.isJsonArray() ? (args.getAsJsonArray().isEmpty() ? null : args.getAsJsonArray().get(0)) : args;
            if (first != null) {
                easingArg = first.getAsDouble();
            }
        }

        return new Channel.Keyframe(time, pre, post, lerp, easing, easingArg);
    }

    // keyframe time in seconds, NaN or infinity breaks keyframe order and animation length
    private static double time(String key) {
        double time = Double.parseDouble(key);
        if (!Double.isFinite(time)) {
            throw new JsonParseException("invalid keyframe time '" + key + "'");
        }

        return time;
    }

    private static JsonElement unwrap(JsonElement element) {
        return element.isJsonObject() ? element.getAsJsonObject().get("vector") : element;
    }

    // array of numbers or Molang strings, single value goes to all three axes
    private static Molang.Expr[] vector(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            Molang.Expr[] vector = new Molang.Expr[3];
            for (int i = 0; i < 3; i++) {
                vector[i] = i < array.size() ? value(array.get(i)) : ZERO;
            }

            return vector;
        }

        Molang.Expr single = value(element);
        return new Molang.Expr[]{single, single, single};
    }

    private static Molang.Expr value(JsonElement element) {
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return Molang.constant(primitive.getAsDouble());
        }

        String source = primitive.getAsString().trim();
        return source.isEmpty() ? ZERO : Molang.compile(source);
    }
}
