package com.yamikhal.playeremotes.neoforge;

//? if neoforge {
/*import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.network.EmotePayload;
import com.yamikhal.playeremotes.platform.Platform;
import com.yamikhal.playeremotes.server.EmotePermissions;
import com.yamikhal.playeremotes.server.EmoteTracker;
import com.yamikhal.playeremotes.server.ServerCommands;
import com.yamikhal.playeremotes.server.ServerPacks;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.nio.file.Path;

@Mod(PlayerEmotes.MOD_ID)
public class PlayerEmotesNeoForge {

    public PlayerEmotesNeoForge(IEventBus modBus, Dist dist) {
        PlayerEmotes.init(new NeoForgePlatform());
        modBus.addListener(PlayerEmotesNeoForge::registerPayloads);

        NeoForge.EVENT_BUS.addListener(PlayerEvent.StartTracking.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onStartTracking(player, event.getTarget());
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onDisconnect(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onRespawnOrTeleport(player);
            }
        });
        NeoForge.EVENT_BUS.addListener(PermissionGatherEvent.Nodes.class, NeoForgePermissions::register);
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, event -> ServerCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> EmoteTracker.tick(event.getServer()));
        ResourceManagerReloadListener serverPacks = ServerPacks::reload;
        //? if >=1.21.4 {
        /^NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.AddServerReloadListenersEvent.class,
                event -> event.addListener(PlayerEmotes.id("server_emotes"), serverPacks));
        ^///?} else {
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.AddReloadListenerEvent.class, event -> event.addListener(serverPacks));
        //?}

        if (dist.isClient()) {
            PlayerEmotesNeoForgeClient.init(modBus);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional: players without the mod can still join, they just do not see emotes
        PayloadRegistrar registrar = event.registrar(String.valueOf(EmoteNetwork.PROTOCOL)).optional();
        registrar.playToServer(EmotePayload.C2S, EmotePayload.C2S_CODEC,
                (payload, context) -> EmoteNetwork.handleServer((ServerPlayer) context.player(), payload.data()));
        registrar.playToClient(EmotePayload.S2C, EmotePayload.S2C_CODEC,
                (payload, context) -> PlayerEmotesClient.handleMessage(payload.data()));
    }

    static final class NeoForgePlatform implements Platform {

        @Override
        public boolean isModLoaded(String modId) {
            return ModList.get().isLoaded(modId);
        }

        @Override
        public Path configDir() {
            return FMLPaths.CONFIGDIR.get();
        }

        @Override
        public boolean hasPermission(ServerPlayer player, EmotePermissions.Node node) {
            return NeoForgePermissions.has(player, node);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, byte[] message) {
            if (player.connection.hasChannel(EmotePayload.S2C)) {
                PacketDistributor.sendToPlayer(player, new EmotePayload(EmotePayload.S2C, message));
            }
        }
    }
}
*///?}
