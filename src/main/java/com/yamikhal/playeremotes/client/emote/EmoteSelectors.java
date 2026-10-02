package com.yamikhal.playeremotes.client.emote;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// named functions that pick an emote variant group from the current world state (the "selector" field of an
// emote file), other mods can register their own
public final class EmoteSelectors {

    private static final Map<String, Function<Minecraft, String>> SELECTORS = new HashMap<>();

    static {
        // leans against a wall to the left/right, otherwise leans back
        register("lean", minecraft -> {
            Player player = minecraft.player;
            if (player == null) {
                return "back";
            }

            Direction facing = player.getDirection();
            if (isSolid(player, facing.getCounterClockWise())) {
                return "left";
            }

            if (isSolid(player, facing.getClockWise())) {
                return "right";
            }

            return "back";
        });
    }

    private EmoteSelectors() {}

    public static void register(String name, Function<Minecraft, String> selector) {
        SELECTORS.put(name, selector);
    }

    // null if the selector is unknown
    public static String select(String name, Minecraft minecraft) {
        Function<Minecraft, String> selector = SELECTORS.get(name);
        return selector == null ? null : selector.apply(minecraft);
    }

    private static boolean isSolid(Player player, Direction side) {
        Level level = player.level();
        BlockPos pos = player.blockPosition().above().relative(side);
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }
}
