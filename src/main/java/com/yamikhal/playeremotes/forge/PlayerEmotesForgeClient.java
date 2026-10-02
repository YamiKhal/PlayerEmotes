package com.yamikhal.playeremotes.forge;

//? if forge {
/*import com.yamikhal.playeremotes.client.EmoteCommands;
import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;

//? if >=1.21
import net.minecraftforge.network.PacketDistributor;
//? if >=1.21.6 {
/^import net.minecraftforge.eventbus.api.bus.BusGroup;
^///?} else {
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
//?}

final class PlayerEmotesForgeClient {

    private PlayerEmotesForgeClient() {}

    //? if >=1.21.6 {
    /^static void init(BusGroup modBus) {
    ^///?} else
    static void init(IEventBus modBus) {
        PlayerEmotesClient.init(new PlayerEmotesClient.ClientNetworking() {
            @Override
            public boolean canSend() {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                return connection != null && PlayerEmotesForge.CHANNEL.isRemotePresent(connection.getConnection());
            }

            @Override
            public void send(byte[] message) {
                //? if >=1.21 {
                PlayerEmotesForge.CHANNEL.send(PlayerEmotesForge.wrap(message), PacketDistributor.SERVER.noArg());
                //?} else
                /^PlayerEmotesForge.CHANNEL.sendToServer(new PlayerEmotesForge.ToServer(message));^/
            }
        });

        //? if >=1.21.6 {
        /^RegisterKeyMappingsEvent.getBus(modBus).addListener(event -> PlayerEmotesClient.keyMappings().forEach(event::register));
        RegisterClientReloadListenersEvent.getBus(modBus).addListener(event ->
                event.registerReloadListener((ResourceManagerReloadListener) PlayerEmotesClient::reload));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> PlayerEmotesClient.tick(Minecraft.getInstance()));
        RegisterClientCommandsEvent.BUS.addListener(event -> EmoteCommands.register(event.getDispatcher()));
        ^///?} else {
        modBus.addListener((RegisterKeyMappingsEvent event) -> PlayerEmotesClient.keyMappings().forEach(event::register));
        modBus.addListener((RegisterClientReloadListenersEvent event) ->
                event.registerReloadListener((ResourceManagerReloadListener) PlayerEmotesClient::reload));
        MinecraftForge.EVENT_BUS.addListener((RegisterClientCommandsEvent event) -> EmoteCommands.register(event.getDispatcher()));
        //? if >=1.21 {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent.Post event) -> PlayerEmotesClient.tick(Minecraft.getInstance()));
        //?} else {
        /^MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                PlayerEmotesClient.tick(Minecraft.getInstance());
            }
        });
        ^///?}
        //?}
    }
}
*///?}
