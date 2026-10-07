package com.yamikhal.playeremotes.forge;

//? if forge {
/*import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.PlayerEmotesClient;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.platform.Platform;
import com.yamikhal.playeremotes.server.EmotePermissions;
import com.yamikhal.playeremotes.server.EmoteTracker;
import com.yamikhal.playeremotes.server.ServerCommands;
import com.yamikhal.playeremotes.server.ServerPacks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;

import java.nio.file.Path;

//? if >=1.21 {
import io.netty.buffer.Unpooled;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.EventNetworkChannel;
//?} else {
/^import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
^///?}
//? if <1.21.6
import net.minecraftforge.common.MinecraftForge;

@Mod(PlayerEmotes.MOD_ID)
public class PlayerEmotesForge {

    //? if >=1.21 {
    // raw byte channel, EmoteNetwork defines the format. optional keeps vanilla clients/servers compatible
    static final EventNetworkChannel CHANNEL = ChannelBuilder.named(PlayerEmotes.id("main"))
            .networkProtocolVersion(EmoteNetwork.PROTOCOL)
            .optional()
            .eventNetworkChannel();

    public PlayerEmotesForge(FMLJavaModLoadingContext context) {
        PlayerEmotes.init(new ForgePlatform());

        CHANNEL.addListener((CustomPayloadEvent event) -> {
            CustomPayloadEvent.Context source = event.getSource();
            byte[] data = readAll(event.getPayload());
            if (source.isServerSide()) {
                ServerPlayer sender = source.getSender();
                if (sender != null) {
                    source.enqueueWork(() -> EmoteNetwork.handleServer(sender, data));
                }
            } else {
                source.enqueueWork(() -> PlayerEmotesClient.handleMessage(data));
            }

            source.setPacketHandled(true);
        });

        //? if >=1.21.6 {
        /^PlayerEvent.StartTracking.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onStartTracking(player, event.getTarget());
            }
        });
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onDisconnect(player);
            }
        });
        PlayerEvent.PlayerRespawnEvent.BUS.addListener(event -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onRespawn(player);
            }
        });
        PermissionGatherEvent.Nodes.BUS.addListener(ForgePermissions::register);
        RegisterCommandsEvent.BUS.addListener(event -> ServerCommands.register(event.getDispatcher()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(event -> EmoteTracker.tick(ServerLifecycleHooks.getCurrentServer()));
        AddReloadListenerEvent.BUS.addListener(event -> event.addListener((ResourceManagerReloadListener) ServerPacks::reload));

        if (FMLEnvironment.dist.isClient()) {
            PlayerEmotesForgeClient.init(context.getModBusGroup());
        }
        ^///?} else {
        registerPlayerEvents();
        if (FMLEnvironment.dist.isClient()) {
            PlayerEmotesForgeClient.init(context.getModEventBus());
        }
        //?}
    }
    //?} else {
    /^private static final String PROTOCOL = String.valueOf(EmoteNetwork.PROTOCOL);
    // accepting a missing channel keeps vanilla clients/servers compatible
    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(PlayerEmotes.id("main"),
            () -> PROTOCOL, NetworkRegistry.acceptMissingOr(PROTOCOL), NetworkRegistry.acceptMissingOr(PROTOCOL));

    record ToServer(byte[] data) {}

    record ToClient(byte[] data) {}

    public PlayerEmotesForge() {
        PlayerEmotes.init(new ForgePlatform());

        CHANNEL.messageBuilder(ToServer.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buf) -> buf.writeBytes(message.data()))
                .decoder(buf -> new ToServer(readAll(buf)))
                .consumerMainThread((message, context) -> {
                    ServerPlayer sender = context.get().getSender();
                    if (sender != null) {
                        EmoteNetwork.handleServer(sender, message.data());
                    }
                })
                .add();
        CHANNEL.messageBuilder(ToClient.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buf) -> buf.writeBytes(message.data()))
                .decoder(buf -> new ToClient(readAll(buf)))
                .consumerMainThread((message, context) -> PlayerEmotesClient.handleMessage(message.data()))
                .add();

        registerPlayerEvents();
        if (FMLEnvironment.dist.isClient()) {
            PlayerEmotesForgeClient.init(FMLJavaModLoadingContext.get().getModEventBus());
        }
    }
    ^///?}

    //? if <1.21.6 {
    private static void registerPlayerEvents() {
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.StartTracking event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onStartTracking(player, event.getTarget());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onDisconnect(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                EmoteTracker.onRespawn(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener(ForgePermissions::register);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ServerCommands.register(event.getDispatcher()));
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> event.addListener((ResourceManagerReloadListener) ServerPacks::reload));
        //? if >=1.21 {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent.Post event) -> EmoteTracker.tick(ServerLifecycleHooks.getCurrentServer()));
        //?} else {
        /^MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                EmoteTracker.tick(ServerLifecycleHooks.getCurrentServer());
            }
        });
        ^///?}
    }
    //?}

    static byte[] readAll(FriendlyByteBuf buf) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        return data;
    }

    //? if >=1.21 {
    static FriendlyByteBuf wrap(byte[] message) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(message));
    }
    //?}

    static final class ForgePlatform implements Platform {

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
            return ForgePermissions.has(player, node);
        }

        @Override
        public void sendToPlayer(ServerPlayer player, byte[] message) {
            //? if >=1.21 {
            if (CHANNEL.isRemotePresent(player.connection.getConnection())) {
                CHANNEL.send(wrap(message), PacketDistributor.PLAYER.with(player));
            }
            //?} else {
            /^if (CHANNEL.isRemotePresent(player.connection.connection)) {
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ToClient(message));
            }
            ^///?}
        }
    }
}
*///?}
