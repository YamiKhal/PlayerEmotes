package com.yamikhal.playeremotes.fabric;

//? if fabric {
import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.platform.Platform;
import com.yamikhal.playeremotes.server.EmotePermissions;
import com.yamikhal.playeremotes.server.EmoteTracker;
import com.yamikhal.playeremotes.server.FabricPermissions;
import com.yamikhal.playeremotes.server.ServerCommands;
import com.yamikhal.playeremotes.server.ServerPacks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.nio.file.Path;

//? if >=1.20.5 {
import com.yamikhal.playeremotes.network.EmotePayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
*///?}

public class PlayerEmotesFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        PlayerEmotes.init(new FabricPlatform());

        //? if >=1.20.5 {
        //? if >=26.1 {
        /*PayloadTypeRegistry.serverboundPlay().register(EmotePayload.C2S, EmotePayload.C2S_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EmotePayload.S2C, EmotePayload.S2C_CODEC);
        *///?} else {
        PayloadTypeRegistry.playC2S().register(EmotePayload.C2S, EmotePayload.C2S_CODEC);
        PayloadTypeRegistry.playS2C().register(EmotePayload.S2C, EmotePayload.S2C_CODEC);
        //?}
        ServerPlayNetworking.registerGlobalReceiver(EmotePayload.C2S,
                (payload, context) -> EmoteNetwork.handleServer(context.player(), payload.data()));
        //?} else {
        /*ServerPlayNetworking.registerGlobalReceiver(EmoteNetwork.C2S, (server, player, handler, buf, sender) -> {
            byte[] data = readAll(buf);
            server.execute(() -> EmoteNetwork.handleServer(player, data));
        });
        *///?}

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ServerCommands.register(dispatcher));
        ServerTickEvents.END_SERVER_TICK.register(EmoteTracker::tick);
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return PlayerEmotes.id("server_emotes");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                ServerPacks.reload(manager);
            }
        });
        EntityTrackingEvents.START_TRACKING.register((entity, player) -> EmoteTracker.onStartTracking(player, entity));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EmoteTracker.onDisconnect(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> EmoteTracker.onRespawnOrTeleport(newPlayer));
    }

    //? if <1.20.5 {
    /*static byte[] readAll(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }
    *///?}

    static final class FabricPlatform implements Platform {

        @Override
        public boolean isModLoaded(String modId) {
            return FabricLoader.getInstance().isModLoaded(modId);
        }

        @Override
        public Path configDir() {
            return FabricLoader.getInstance().getConfigDir();
        }

        @Override
        public boolean hasPermission(ServerPlayer player, EmotePermissions.Node node) {
            return FabricPermissions.has(player, node);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, byte[] message) {
            //? if >=1.20.5 {
            if (ServerPlayNetworking.canSend(player, EmotePayload.S2C)) {
                ServerPlayNetworking.send(player, new EmotePayload(EmotePayload.S2C, message));
            }
            //?} else {
            /*if (ServerPlayNetworking.canSend(player, EmoteNetwork.S2C)) {
                FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeBytes(message);
                ServerPlayNetworking.send(player, EmoteNetwork.S2C, buf);
            }
            *///?}
        }
    }
}
//?}
