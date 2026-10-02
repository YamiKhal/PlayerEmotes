package com.yamikhal.playeremotes.anim;

import java.util.Locale;

// mirrors the bone layout of blockbench/player_emote_template.geo.json so the Blockbench preview matches the game:
// body (root, moves the whole player)
// ├── torso (pivot at the waist)
// │   ├── head
// │   ├── right_arm
// │   └── left_arm
// ├── right_leg
// └── left_leg
// coordinates are Java model space in pixels (Y points down, 0 = neck, 24 = feet), origin is where the
// vanilla model part sits at rest, pivot is the Blockbench bone pivot the animation rotates around
public enum Part {
    BODY(null, 0, 12, 0, 0, 12, 0),
    TORSO(BODY, 0, 12, 0, 0, 0, 0),
    HEAD(TORSO, 0, 0, 0, 0, 0, 0),
    RIGHT_ARM(TORSO, -5, 2, 0, -5, 2, 0),
    LEFT_ARM(TORSO, 5, 2, 0, 5, 2, 0),
    RIGHT_LEG(BODY, -1.9F, 12, 0, -1.9F, 12, 0),
    LEFT_LEG(BODY, 1.9F, 12, 0, 1.9F, 12, 0);

    public static final Part[] VALUES = values();
    // parts that map to a vanilla model part (everything except the root)
    public static final Part[] MODEL_PARTS = {TORSO, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG};

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

    // resolves Blockbench bone names (right_arm, rightArm, RightArm), null for bones outside the skeleton
    public static Part byBoneName(String bone) {
        return switch (bone.toLowerCase(Locale.ROOT).replace("_", "")) {
            case "body", "root" -> BODY;
            case "torso" -> TORSO;
            case "head" -> HEAD;
            case "rightarm" -> RIGHT_ARM;
            case "leftarm" -> LEFT_ARM;
            case "rightleg" -> RIGHT_LEG;
            case "leftleg" -> LEFT_LEG;
            default -> null;
        };
    }
}
