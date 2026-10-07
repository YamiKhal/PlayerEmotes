package com.yamikhal.playeremotes.neoforge;

//? if neoforge {
/*import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.EmoteCommands;
import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.network.EmotePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

//? if >=1.21.6 {
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
//?} else
/^import net.neoforged.neoforge.network.PacketDistributor;^/
//? if >=1.21.4 {
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
//?} else
/^import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;^/

final class PlayerEmotesNeoForgeClient {

    private PlayerEmotesNeoForgeClient() {}

    static void init(IEventBus modBus) {
        PlayerEmotesClient.init(new PlayerEmotesClient.ClientNetworking() {
            @Override
            public boolean canSend() {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                return connection != null && connection.hasChannel(EmotePayload.C2S);
            }

            @Override
            public void send(byte[] message) {
                //? if >=1.21.6 {
                ClientPacketDistributor.sendToServer(new EmotePayload(EmotePayload.C2S, message));
                //?} else
                /^PacketDistributor.sendToServer(new EmotePayload(EmotePayload.C2S, message));^/
            }
        });

        ResourceManagerReloadListener reloader = PlayerEmotesClient::reload;
        //? if >=1.21.4 {
        modBus.addListener(AddClientReloadListenersEvent.class,
                event -> event.addListener(PlayerEmotes.id("emotes"), reloader));
        //?} else {
        /^modBus.addListener(RegisterClientReloadListenersEvent.class, event -> event.registerReloadListener(reloader));
        ^///?}
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> PlayerEmotesClient.keyMappings().forEach(event::register));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> PlayerEmotesClient.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> EmoteCommands.register(event.getDispatcher()));
    }
}
*///?}
