package com.yamikhal.playeremotes.client.emote;

import com.yamikhal.playeremotes.PlayerEmotes;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

// named functions picking an emote variant group from the world state ("selector" of an emote file), other mods
// can register their own
public final class EmoteSelectors {

    // concurrent, Forge and NeoForge set up mods in parallel and other mods may register then
    private static final Map<String, Function<Minecraft, String>> SELECTORS = new ConcurrentHashMap<>();

    static {
        // leans against a wall to the left/right, else leans back
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

    // null if the selector is unknown or failed
    @Nullable
    public static String select(String name, Minecraft minecraft) {
        Function<Minecraft, String> selector = SELECTORS.get(name);
        if (selector == null) {
            return null;
        }

        // selectors can come from other mods, a broken one must not stop the emote
        try {
            return selector.apply(minecraft);
        } catch (RuntimeException e) {
            PlayerEmotes.LOGGER.error("Emote selector '{}' failed", name, e);
            return null;
        }
    }

    private static boolean isSolid(Player player, Direction side) {
        Level level = player.level();
        BlockPos pos = player.blockPosition().above().relative(side);
        return level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }
}
