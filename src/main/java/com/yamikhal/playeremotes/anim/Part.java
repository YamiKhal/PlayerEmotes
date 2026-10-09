package com.yamikhal.playeremotes.anim;

import java.util.Locale;

// same bone layout as workspace/player_emote_template.geo.json, so the Blockbench preview matches the game:
// body (root, moves the whole player)
// ├── torso (pivot at the waist)
// │   ├── head
// │   ├── right_arm
// │   │   └── right_lower_arm
// │   └── left_arm
// │       └── left_lower_arm
// ├── right_leg
// │   └── right_lower_leg
// └── left_leg
//     └── left_lower_leg
// Java model space in pixels (y down, 0 = neck, 24 = feet), origin is where the vanilla part rests, pivot the
// Blockbench bone pivot the animation rotates around. lower halves have no model part, they bend their limb at
// its middle (elbow, knee), see Bend
public enum Part {
    BODY(null, 0, 12, 0, 0, 12, 0),
    TORSO(BODY, 0, 12, 0, 0, 0, 0),
    HEAD(TORSO, 0, 0, 0, 0, 0, 0),
    RIGHT_ARM(TORSO, -5, 2, 0, -5, 2, 0),
    LEFT_ARM(TORSO, 5, 2, 0, 5, 2, 0),
    RIGHT_LEG(BODY, -1.9F, 12, 0, -1.9F, 12, 0),
    LEFT_LEG(BODY, 1.9F, 12, 0, 1.9F, 12, 0),
    RIGHT_LOWER_ARM(RIGHT_ARM, -6, 6, 0, -6, 6, 0),
    LEFT_LOWER_ARM(LEFT_ARM, 6, 6, 0, 6, 6, 0),
    RIGHT_LOWER_LEG(RIGHT_LEG, -1.9F, 18, 0, -1.9F, 18, 0),
    LEFT_LOWER_LEG(LEFT_LEG, 1.9F, 18, 0, 1.9F, 18, 0);

    public static final Part[] VALUES = values();
    // parts with a vanilla model part (all but the root and lower halves)
    public static final Part[] MODEL_PARTS = {TORSO, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG};
    // limbs that bend at their middle, and their lower halves (same order)
    public static final Part[] LIMBS = {RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG};
    public static final Part[] LOWER_LIMBS = {RIGHT_LOWER_ARM, LEFT_LOWER_ARM, RIGHT_LOWER_LEG, LEFT_LOWER_LEG};

    public final Part parent;
    public final float pivotX;
    public final float pivotY;
    public final float pivotZ;
    public final float originX;
    public final float originY;
    public final float originZ;

    Part(Part parent, float pivotX, float pivotY, float pivotZ, float originX, float originY, float originZ) {
        this.parent = parent;
        this.pivotX = pivotX;
        this.pivotY = pivotY;
        this.pivotZ = pivotZ;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public float origin(int axis) {
        return axis == 0 ? this.originX : axis == 1 ? this.originY : this.originZ;
    }

    public boolean isLimb() {
        return this == RIGHT_ARM || this == LEFT_ARM || this == RIGHT_LEG || this == LEFT_LEG;
    }

    // joint height below the limb's pivot in pixels: 4 for elbows, 6 for knees
    public float jointY() {
        return this.lower().pivotY - this.pivotY;
    }

    // lower half of a limb
    public Part lower() {
        return switch (this) {
            case RIGHT_ARM -> RIGHT_LOWER_ARM;
            case LEFT_ARM -> LEFT_LOWER_ARM;
            case RIGHT_LEG -> RIGHT_LOWER_LEG;
            case LEFT_LEG -> LEFT_LOWER_LEG;
            default -> throw new IllegalArgumentException(this + " is not a limb");
        };
    }

    // resolves bone names (right_arm, rightArm, RightArm), null outside the skeleton. also takes the names of
    // AzureLib / GeckoLib humanoid and armor rigs (bipedHead, armorRightArm, arm_right), there body means the torso
    public static Part byBoneName(String bone) {
        String key = key(bone);
        Part part = byKey(key);
        if (part != null) {
            return part;
        }

        for (String prefix : RIG_PREFIXES) {
            if (key.startsWith(prefix) && key.length() > prefix.length()) {
                String rest = key.substring(prefix.length());
                return rest.equals("body") ? TORSO : byKey(rest);
            }
        }

        return null;
    }

    // whether the bone name is one of ours and not a rig alias, ours win when an animation has both
    public static boolean isOwnBoneName(String bone) {
        return byKey(key(bone)) != null;
    }

    // humanoid rig prefixes, longest first
    private static final String[] RIG_PREFIXES = {"armorbiped", "biped", "armor"};

    private static String key(String bone) {
        return bone.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(".", "").replace(" ", "");
    }

    private static Part byKey(String key) {
        return switch (key) {
            case "body", "root" -> BODY;
            case "torso" -> TORSO;
            case "head" -> HEAD;
            case "rightarm", "armright" -> RIGHT_ARM;
            case "leftarm", "armleft" -> LEFT_ARM;
            case "rightleg", "legright" -> RIGHT_LEG;
            case "leftleg", "legleft" -> LEFT_LEG;
            case "rightlowerarm", "rightforearm" -> RIGHT_LOWER_ARM;
            case "leftlowerarm", "leftforearm" -> LEFT_LOWER_ARM;
            case "rightlowerleg", "rightshin" -> RIGHT_LOWER_LEG;
            case "leftlowerleg", "leftshin" -> LEFT_LOWER_LEG;
            default -> null;
        };
    }
}
