package com.yamikhal.playeremotes.fabric;

//? if fabric {
import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.EmoteCommands;
import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;

//? if >=26.1 {
/*import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?} else
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

//? if >=1.20.5 {
import com.yamikhal.playeremotes.network.EmotePayload;
//?} else {
/*import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
*///?}

public class PlayerEmotesFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PlayerEmotesClient.init(new PlayerEmotesClient.ClientNetworking() {
            @Override
            public boolean canSend() {
                //? if >=1.20.5 {
                return ClientPlayNetworking.canSend(EmotePayload.C2S);
                //?} else
                /*return ClientPlayNetworking.canSend(EmoteNetwork.C2S);*/
            }

            @Override
            public void send(byte[] message) {
                //? if >=1.20.5 {
                ClientPlayNetworking.send(new EmotePayload(EmotePayload.C2S, message));
                //?} else {
                /*FriendlyByteBuf buf = PacketByteBufs.create();
                buf.writeBytes(message);
                ClientPlayNetworking.send(EmoteNetwork.C2S, buf);
                *///?}
            }
        });

        //? if >=1.20.5 {
        ClientPlayNetworking.registerGlobalReceiver(EmotePayload.S2C,
                (payload, context) -> PlayerEmotesClient.handleMessage(payload.data()));
        //?} else {
        /*ClientPlayNetworking.registerGlobalReceiver(EmoteNetwork.S2C, (client, handler, buf, sender) -> {
            byte[] data = PlayerEmotesFabric.readAll(buf);
            client.execute(() -> PlayerEmotesClient.handleMessage(data));
        });
        *///?}

        //? if >=26.1 {
        /*PlayerEmotesClient.keyMappings().forEach(KeyMappingHelper::registerKeyMapping);
        *///?} else
        PlayerEmotesClient.keyMappings().forEach(KeyBindingHelper::registerKeyBinding);
        ClientTickEvents.END_CLIENT_TICK.register(PlayerEmotesClient::tick);
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> EmoteCommands.register(dispatcher));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return PlayerEmotes.id("emotes");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                PlayerEmotesClient.reload(manager);
            }
        });
    }
}
//?}
