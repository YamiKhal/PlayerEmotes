package com.yamikhal.playeremotes.client.animation;

import com.yamikhal.playeremotes.anim.Query;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

// answers query.* keyframes for one emoting player, read live when a frame samples, so nothing gets computed for
// animations that never ask. one per playback, player set on tick, partial tick when the frame is made
public final class EmoteQueries implements Query.Source {

    // previews show the local player
    public static final EmoteQueries LOCAL = new EmoteQueries(true);
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final boolean local;
    @Nullable
    Player player;
    float partialTick = 1;

    EmoteQueries() {
        this(false);
    }

    private EmoteQueries(boolean local) {
        this.local = local;
    }

    @Override
    public double query(Query query) {
        Player player = this.local ? Minecraft.getInstance().player : this.player;
        if (player == null) {
            return 0;
        }

        float pt = this.partialTick;
        return switch (query) {
            case BODY_Y_ROTATION -> Mth.wrapDegrees(Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot));
            case CARDINAL_FACING_2D -> player.getDirection().get3DDataValue();
            case DISTANCE_FROM_CAMERA -> {
                Entity camera = Minecraft.getInstance().getCameraEntity();
                yield camera == null ? 0 : camera.getEyePosition(pt).distanceTo(player.getPosition(pt));
            }
            case EQUIPMENT_COUNT -> {
                int count = 0;
                for (EquipmentSlot slot : ARMOR) {
                    if (!player.getItemBySlot(slot).isEmpty()) {
                        count++;
                    }
                }

                yield count;
            }
            // from position change, remote players have no reliable motion on the client
            case GROUND_SPEED -> Math.sqrt(Mth.square(player.getX() - player.xo) + Mth.square(player.getZ() - player.zo)) * 20;
            case VERTICAL_SPEED -> (player.getY() - player.yo) * 20;
            case HAS_HEAD_GEAR -> bool(!player.getItemBySlot(EquipmentSlot.HEAD).isEmpty());
            case HEAD_X_ROTATION -> player.getViewXRot(pt);
            case HEAD_Y_ROTATION -> Mth.wrapDegrees(Mth.rotLerp(pt, player.yHeadRotO, player.yHeadRot)
                    - Mth.rotLerp(pt, player.yBodyRotO, player.yBodyRot));
            case HEALTH -> player.getHealth();
            case MAX_HEALTH -> player.getMaxHealth();
            case HURT_TIME -> player.hurtTime;
            case IS_IN_LAVA -> bool(player.isInLava());
            case IS_IN_WATER -> bool(player.isInWater());
            case IS_IN_WATER_OR_RAIN -> bool(player.isInWaterOrRain());
            case IS_ON_FIRE -> bool(player.isOnFire());
            case IS_ON_GROUND -> bool(player.onGround());
            case IS_RIDING -> bool(player.isPassenger());
            case IS_SNEAKING -> bool(player.isCrouching());
            case IS_SPRINTING -> bool(player.isSprinting());
            case IS_SWIMMING -> bool(player.isSwimming());
            case IS_USING_ITEM -> bool(player.isUsingItem());
            case LIMB_SWING -> player.walkAnimation.position(pt);
            case LIMB_SWING_AMOUNT -> player.walkAnimation.speed(pt);
            case MOON_PHASE -> Math.floorMod(dayTime(player) / 24000L, 8);
            case PLAYER_LEVEL -> player.experienceLevel;
            case TIME_OF_DAY -> Math.floorMod(dayTime(player), 24000L) / 24000.0;
            case YAW_SPEED -> Mth.wrapDegrees(player.getYRot() - player.yRotO) * 20;
        };
    }

    private static long dayTime(Player player) {
        //? if >=26.1 {
        /*return player.level().getDefaultClockTime();
        *///?} else
        return player.level().getDayTime();
    }

    private static double bool(boolean value) {
        return value ? 1 : 0;
    }
}
