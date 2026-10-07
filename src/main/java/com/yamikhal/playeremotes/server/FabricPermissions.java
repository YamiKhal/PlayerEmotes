package com.yamikhal.playeremotes.server;

import com.yamikhal.playeremotes.PlayerEmotes;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

// fabric-permissions-api (permission API LuckPerms and others implement on Fabric), through reflection and only
// when installed, so no build dependency
public final class FabricPermissions {

    private static final String MOD_ID = "fabric-permissions-api-v0";
    private static boolean looked;
    @Nullable
    private static Method check;

    private FabricPermissions() {}

    public static boolean has(ServerPlayer player, EmotePermissions.Node node) {
        Method method = method();
        if (method != null) {
            try {
                return (Boolean) method.invoke(null, player, node.fullName(), node.opLevel);
            } catch (ReflectiveOperationException | RuntimeException e) {
                PlayerEmotes.LOGGER.warn("fabric-permissions-api check failed, using operator levels", e);
                check = null;
            }
        }

        return OpLevel.has(player, node.opLevel);
    }

    // Permissions.check(Entity, String, int defaultRequiredLevel)
    @Nullable
    private static synchronized Method method() {
        if (!looked) {
            looked = true;
            if (PlayerEmotes.platform().isModLoaded(MOD_ID)) {
                try {
                    for (Method candidate : Class.forName("me.lucko.fabric.api.permissions.v0.Permissions").getMethods()) {
                        Class<?>[] params = candidate.getParameterTypes();
                        if (candidate.getName().equals("check") && params.length == 3 && params[0].isAssignableFrom(ServerPlayer.class)
                                && params[1] == String.class && params[2] == int.class) {
                            check = candidate;
                            break;
                        }
                    }
                } catch (ReflectiveOperationException | LinkageError e) {
                    PlayerEmotes.LOGGER.warn("fabric-permissions-api is installed but could not be used", e);
                }
            }
        }

        return check;
    }
}
