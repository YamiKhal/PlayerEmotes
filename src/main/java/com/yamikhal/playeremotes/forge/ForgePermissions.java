package com.yamikhal.playeremotes.forge;

//? if forge {
/*import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.server.EmotePermissions;
import com.yamikhal.playeremotes.server.OpLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

import java.util.EnumMap;
import java.util.Map;

// mod nodes in Forge's permission API, which permission plugins hook into, unhandled nodes fall back to the
// node's operator level
final class ForgePermissions {

    private static final Map<EmotePermissions.Node, PermissionNode<Boolean>> NODES = new EnumMap<>(EmotePermissions.Node.class);

    static {
        for (EmotePermissions.Node node : EmotePermissions.Node.values()) {
            NODES.put(node, new PermissionNode<>(PlayerEmotes.MOD_ID, node.path, PermissionTypes.BOOLEAN,
                    (player, uuid, context) -> OpLevel.has(player, node.opLevel)));
        }
    }

    private ForgePermissions() {}

    static void register(PermissionGatherEvent.Nodes event) {
        event.addNodes(NODES.values().toArray(new PermissionNode<?>[0]));
    }

    static boolean has(ServerPlayer player, EmotePermissions.Node node) {
        try {
            return PermissionAPI.getPermission(player, NODES.get(node));
        } catch (RuntimeException e) {
            // nodes not registered (e.g. asked before the server finished starting)
            return OpLevel.has(player, node.opLevel);
        }
    }
}
*///?}
