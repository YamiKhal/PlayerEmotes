package com.yamikhal.playeremotes.anim;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// player state queries keyframes can read (query.health, q.is_on_ground), names as in Bedrock, GeckoLib and
// AzureLib so their animations work unchanged. resolved when the Molang compiles, the client answers them per frame
// (see EmoteQueries), previews and unknown players get 0. rotations in degrees, speeds in blocks per second
public enum Query {
    BODY_Y_ROTATION("body_y_rotation"),
    CARDINAL_FACING_2D("cardinal_facing_2d"),
    DISTANCE_FROM_CAMERA("distance_from_camera"),
    EQUIPMENT_COUNT("equipment_count"),
    GROUND_SPEED("ground_speed"),
    HAS_HEAD_GEAR("has_head_gear"),
    // pitch, down positive
    HEAD_X_ROTATION("head_x_rotation"),
    // head yaw relative to the body, so [q.head_x_rotation, q.head_y_rotation, 0] on the head looks around
    HEAD_Y_ROTATION("head_y_rotation"),
    HEALTH("health"),
    HURT_TIME("hurt_time"),
    IS_IN_LAVA("is_in_lava"),
    IS_IN_WATER("is_in_water"),
    IS_IN_WATER_OR_RAIN("is_in_water_or_rain"),
    IS_ON_FIRE("is_on_fire"),
    IS_ON_GROUND("is_on_ground"),
    IS_RIDING("is_riding"),
    IS_SNEAKING("is_sneaking"),
    IS_SPRINTING("is_sprinting"),
    IS_SWIMMING("is_swimming"),
    IS_USING_ITEM("is_using_item"),
    LIMB_SWING("limb_swing"),
    LIMB_SWING_AMOUNT("limb_swing_amount"),
    MAX_HEALTH("max_health"),
    MOON_PHASE("moon_phase"),
    PLAYER_LEVEL("player_level"),
    // 0 to 1 over the day, 0 at sunrise like GeckoLib
    TIME_OF_DAY("time_of_day"),
    VERTICAL_SPEED("vertical_speed"),
    YAW_SPEED("yaw_speed");

    public static final Query[] VALUES = values();
    private static final Map<String, Query> BY_NAME = new HashMap<>();

    static {
        for (Query query : VALUES) {
            BY_NAME.put("query." + query.id, query);
        }
    }

    public final String id;

    // answers queries for one player
    @FunctionalInterface
    public interface Source {

        double query(Query query);
    }

    Query(String id) {
        this.id = id;
    }

    // full name like query.health (q. already expanded), null if not a player query
    public static Query byName(String name) {
        return BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }
}
