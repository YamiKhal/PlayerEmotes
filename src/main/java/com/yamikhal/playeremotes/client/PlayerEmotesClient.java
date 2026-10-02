package com.yamikhal.playeremotes.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.animation.PartnerLink;
import com.yamikhal.playeremotes.client.animation.EmoteSoundInstance;
import com.yamikhal.playeremotes.client.compat.EmfCompat;
import com.yamikhal.playeremotes.client.config.EmoteConfig;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.emote.ServerPackClient;
import com.yamikhal.playeremotes.client.gui.Messages;
import com.yamikhal.playeremotes.client.gui.QuickWheelScreen;
import com.yamikhal.playeremotes.client.gui.Screens;
import com.yamikhal.playeremotes.client.preview.EmotePreview;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// client entry point shared by all loaders
public final class PlayerEmotesClient {

    //? if >=1.21.9 {
    /*private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(PlayerEmotes.id("main"));
    *///?} else
    private static final String KEY_CATEGORY = "category.playeremotes";
    public static final KeyMapping OPEN_WHEEL = new KeyMapping("key.playeremotes.wheel", InputConstants.KEY_B, KEY_CATEGORY);
    // play the emotes in slots 1-4 of the first wheel page, unbound by default
    public static final KeyMapping[] PLAY_SLOT = {unbound("key.playeremotes.slot_1"), unbound("key.playeremotes.slot_2"),
            unbound("key.playeremotes.slot_3"), unbound("key.playeremotes.slot_4")};
    // reloads only the emote files, for pack makers, unbound by default
    public static final KeyMapping RELOAD = unbound("key.playeremotes.reload");
    // accepts a partner emote request (or joins the waiting player in front), unbound by default
    public static final KeyMapping ACCEPT = unbound("key.playeremotes.accept");

    // remote emotes that started longer ago than this (e.g. replays for late joiners) play without sound
    private static final int SOUND_START_WINDOW_TICKS = 10;

    private static ClientNetworking networking;
    private static EmoteConfig config;

    private PlayerEmotesClient() {}

    public static void init(ClientNetworking networking) {
        PlayerEmotesClient.networking = networking;
        config = new EmoteConfig(PlayerEmotes.platform().configDir().resolve(PlayerEmotes.MOD_ID + ".json"));
        config.load();
        EmfCompat.init();
    }

    public static EmoteConfig config() {
        return config;
    }

    // every key mapping of the mod, for the loaders to register
    public static List<KeyMapping> keyMappings() {
        List<KeyMapping> keys = new ArrayList<>();
        keys.add(OPEN_WHEEL);
        keys.addAll(List.of(PLAY_SLOT));
        keys.add(ACCEPT);
        keys.add(RELOAD);
        return keys;
    }

    // reloads emote packs, registered as a client resource reload listener
    public static void reload(ResourceManager manager) {
        EmoteRegistry.reload(manager);
    }

    // re-reads the emote files of the current resource packs without a full resource reload
    public static void reloadEmotes() {
        Minecraft minecraft = Minecraft.getInstance();
        EmoteRegistry.reload(minecraft.getResourceManager());
        EmotePreview.reset();
        Messages.show(Component.translatable("playeremotes.message.reloaded", EmoteRegistry.all().size()));
    }

    public static void tick(Minecraft minecraft) {
        EmotePlayers.tick(minecraft);
        DevShowcase.tick(minecraft);
        DevPartnerTest.tick(minecraft);

        boolean canUse = Screens.current() == null && minecraft.player != null && minecraft.player == minecraft.getCameraEntity();
        while (OPEN_WHEEL.consumeClick()) {
            // whether it is still held matters for key repeats and "hold to open"
            if (canUse) {
                Screens.open(new QuickWheelScreen(null, OPEN_WHEEL.isDown()));
            }
        }

        for (int slot = 0; slot < PLAY_SLOT.length; slot++) {
            while (PLAY_SLOT[slot].consumeClick()) {
                if (canUse) {
                    playSlot(slot);
                }
            }
        }

        while (RELOAD.consumeClick()) {
            reloadEmotes();
        }

        while (ACCEPT.consumeClick()) {
            if (canUse) {
                acceptPartner();
            }
        }

        LocalEmotes.tick(minecraft);
    }

    // plays an emote as the local player
    public static void play(Emote emote) {
        LocalEmotes.play(emote);
    }

    // stops the local player's emote
    public static void stop() {
        LocalEmotes.stop();
    }

    // accepts the newest partner request, or joins the waiting player in front
    public static void acceptPartner() {
        LocalEmotes.accept();
    }

    // the newest partner emote request to the local player, null if there is none
    @Nullable
    public static PendingRequest pendingRequest() {
        return PartnerRequests.latest();
    }

    // applies a change of EmoteConfig#acceptRequests
    public static void onAcceptRequestsChanged() {
        if (!config.acceptRequests) {
            PartnerRequests.clear();
        }

        LocalEmotes.sendHello();
    }

    // applies a change of EmoteConfig#showOtherEmotes
    public static void onShowOtherEmotesChanged() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!config.showOtherEmotes && minecraft.player != null) {
            EmotePlayers.stopAllExcept(minecraft.player.getUUID());
        }
    }

    // whether the local player can join the emote another player is playing
    public static boolean canSyncWith(UUID other) {
        return LocalEmotes.canSyncWith(other);
    }

    // joins the emote another player is playing, in step with them
    public static void syncWith(UUID other) {
        LocalEmotes.syncWith(other);
    }

    // handles a message from the server, must run on the client thread
    public static void handleMessage(byte[] data) {
        EmoteNetwork.ClientMessage message = EmoteNetwork.decodeClient(data);
        Minecraft minecraft = Minecraft.getInstance();
        UUID self = minecraft.player == null ? null : minecraft.player.getUUID();
        if (message instanceof EmoteNetwork.RemotePlay play) {
            // our own emotes are played locally the moment they start
            if (play.player().equals(self) || !config.showOtherEmotes) {
                return;
            }

            EmotePlayback playback = EmotePlayers.start(play.player(), null, play.animation(), play.options(), play.elapsedTicks());
            Player source = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(play.player());
            if (source != null && play.options().sound() != null && config.hearOtherSounds
                    && play.elapsedTicks() <= SOUND_START_WINDOW_TICKS) {
                EmoteSoundInstance.play(source, playback, play.options().sound(), false);
            }
        } else if (message instanceof EmoteNetwork.RemoteStop stop) {
            if (!stop.player().equals(self)) {
                EmotePlayers.stop(stop.player());
            }
        } else if (message instanceof EmoteNetwork.Denied denied) {
            LocalEmotes.denied(denied.sequence(), denied.reason());
        } else if (message instanceof EmoteNetwork.Rules rules) {
            LocalEmotes.rulesReceived(rules.rules());
        } else if (message instanceof EmoteNetwork.PartnerPlay play) {
            startPartner(minecraft, play, play.starter(), play.starterAnimation(), PartnerLink.starter(play), self);
            startPartner(minecraft, play, play.partner(), play.partnerAnimation(), PartnerLink.partner(play), self);
            PartnerRequests.remove(play.starter());
            if (play.starter().equals(self) || play.partner().equals(self)) {
                LocalEmotes.partnerStarted(play.id());
            }
        } else if (message instanceof EmoteNetwork.PartnerEnd end) {
            EmotePlayers.stopPartner(end.starter(), end.id());
            EmotePlayers.stopPartner(end.partner(), end.id());
            if (end.starter().equals(self) || end.partner().equals(self)) {
                LocalEmotes.partnerEnded(end.id());
            }
        } else if (message instanceof EmoteNetwork.Request request) {
            if (config.acceptRequests) {
                PartnerRequests.received(request);
            }
        } else if (message instanceof EmoteNetwork.RequestCancel cancel) {
            PartnerRequests.remove(cancel.starter());
        } else if (message instanceof EmoteNetwork.Status status) {
            String key = "playeremotes.partner.status." + status.code().name().toLowerCase(Locale.ROOT);
            String name = ChatFormatting.stripFormatting(status.name());
            Messages.overlay(Component.translatable(key, name == null ? "" : name));
        } else if (message instanceof EmoteNetwork.PackManifest manifest) {
            ServerPackClient.onManifest(manifest, PlayerEmotesClient::send);
        } else if (message instanceof EmoteNetwork.PackChunk chunk) {
            ServerPackClient.onChunk(chunk);
        }
    }

    static boolean canSend() {
        return networking != null && networking.canSend();
    }

    static void send(byte[] message) {
        if (canSend()) {
            networking.send(message);
        }
    }

    private static KeyMapping unbound(String name) {
        return new KeyMapping(name, InputConstants.UNKNOWN.getValue(), KEY_CATEGORY);
    }

    // plays the emote in a slot of the first wheel page, or stops it if it is the one playing
    private static void playSlot(int slot) {
        ResourceLocation id = config.wheelSlot(0, slot);
        Emote emote = id == null ? null : EmoteRegistry.get(id);
        LocalPlayer player = Minecraft.getInstance().player;
        if (emote == null || player == null) {
            return;
        }

        EmotePlayback playback = EmotePlayers.get(player.getUUID());
        if (playback != null && !playback.isStopping() && emote.id().equals(playback.emoteId())) {
            stop();
        } else {
            play(emote);
        }
    }

    // starts one of the two players of a partner emote
    private static void startPartner(Minecraft minecraft, EmoteNetwork.PartnerPlay play, UUID player, ResourceLocation animation,
                                     PartnerLink link, @Nullable UUID self) {
        boolean own = player.equals(self);
        if (!own && !config.showOtherEmotes) {
            return;
        }

        // the emote's sound is the starter's, both playing it would double it
        boolean withSound = player.equals(play.starter()) && play.options().sound() != null
                && (own ? config.playEmoteSounds : config.hearOtherSounds) && play.elapsedTicks() <= SOUND_START_WINDOW_TICKS;
        EmotePlayback playback = EmotePlayers.startPartner(player, animation, play.options(), play.elapsedTicks(), link);
        Player source = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(player);
        if (withSound && source != null) {
            EmoteSoundInstance.play(source, playback, play.options().sound(), own);
        }
    }

    // a partner emote request to the local player, names are plain text
    public record PendingRequest(UUID starter, String starterName, String emoteName) {}

    // sends messages to the server, implemented per loader
    public interface ClientNetworking {

        boolean canSend();

        void send(byte[] message);
    }
}
