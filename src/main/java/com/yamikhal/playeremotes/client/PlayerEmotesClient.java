package com.yamikhal.playeremotes.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.animation.EmoteSoundInstance;
import com.yamikhal.playeremotes.client.animation.PartnerLink;
import com.yamikhal.playeremotes.client.compat.EmfCompat;
import com.yamikhal.playeremotes.client.compat.ReplayCompat;
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

// client entry point for all loaders
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
    // accepts a partner request (or joins the waiting player in front), unbound by default
    public static final KeyMapping ACCEPT = unbound("key.playeremotes.accept");

    // remote emotes older than this (e.g. replays for late joiners) play without sound
    private static final int SOUND_START_WINDOW_TICKS = 10;

    private static ClientNetworking networking;
    private static EmoteConfig config;

    private PlayerEmotesClient() {}

    public static void init(ClientNetworking networking) {
        PlayerEmotesClient.networking = networking;
        config = new EmoteConfig(PlayerEmotes.platform().configDir().resolve(PlayerEmotes.MOD_ID + ".json"));
        config.load();
        EmfCompat.init();
        ReplayCompat.init();
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

    // reloads emote packs, registered as client resource reload listener
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

        boolean canUse = Screens.current() == null && minecraft.player != null && minecraft.player == minecraft.getCameraEntity()
                && !EmotePlayers.inReplay();
        while (OPEN_WHEEL.consumeClick()) {
            // still held or not matters for key repeats and "hold to open"
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

    public static void play(Emote emote) {
        LocalEmotes.play(emote);
    }

    public static void stop() {
        LocalEmotes.stop();
    }

    // accepts the newest partner request, or joins the waiting player in front
    public static void acceptPartner() {
        LocalEmotes.accept();
    }

    // newest partner request to the local player, null if none
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

    // whether the local player can join another player's emote
    public static boolean canSyncWith(UUID other) {
        return LocalEmotes.canSyncWith(other);
    }

    // joins another player's emote, in step with them
    public static void syncWith(UUID other) {
        LocalEmotes.syncWith(other);
    }

    // handles a server message, client thread only
    public static void handleMessage(byte[] data) {
        EmoteNetwork.ClientMessage message = EmoteNetwork.decodeClient(data);
        Minecraft minecraft = Minecraft.getInstance();
        EmotePlayers.syncLevel(minecraft);
        LocalEmotes.syncConnection(minecraft);
        boolean replay = EmotePlayers.inReplay();
        // in a replay the local player is only a camera, every recorded player is someone else
        UUID self = minecraft.player == null || replay ? null : minecraft.player.getUUID();
        if (message instanceof EmoteNetwork.RemotePlay play) {
            // own emotes play locally the moment they start, the server only sends them back for recordings
            if (play.player().equals(self) || !config.showOtherEmotes) {
                return;
            }

            EmotePlayback playback = EmotePlayers.startRemote(play.player(), play.animation(), play.options(), play.elapsedTicks(),
                    play.gameTime());
            Player source = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(play.player());
            if (source != null && play.options().sound() != null && config.hearOtherSounds && isFresh(playback)) {
                EmoteSoundInstance.play(source, playback, play.options().sound(), false);
            }
        } else if (message instanceof EmoteNetwork.RemoteStop stop) {
            if (!stop.player().equals(self)) {
                EmotePlayers.stopRemote(stop.player(), stop.gameTime());
            }
        } else if (message instanceof EmoteNetwork.PartnerPlay play) {
            startPartner(minecraft, play, play.starter(), play.starterAnimation(), PartnerLink.starter(play), self);
            startPartner(minecraft, play, play.partner(), play.partnerAnimation(), PartnerLink.partner(play), self);
            PartnerRequests.remove(play.starter());
            if (play.starter().equals(self) || play.partner().equals(self)) {
                LocalEmotes.partnerStarted(play.id());
            }
        } else if (message instanceof EmoteNetwork.PartnerEnd end) {
            EmotePlayers.stopPartner(end.starter(), end.id(), end.gameTime());
            EmotePlayers.stopPartner(end.partner(), end.id(), end.gameTime());
            if (end.starter().equals(self) || end.partner().equals(self)) {
                LocalEmotes.partnerEnded(end.id());
            }
        } else if (message instanceof EmoteNetwork.PackManifest manifest) {
            ServerPackClient.onManifest(manifest, PlayerEmotesClient::send, !replay);
        } else if (message instanceof EmoteNetwork.PackChunk chunk) {
            ServerPackClient.onChunk(chunk);
        } else if (replay) {
            // rest was meant for the recording player: their denials, requests and the server's answers
            return;
        } else if (message instanceof EmoteNetwork.Denied denied) {
            LocalEmotes.denied(denied.sequence(), denied.reason());
        } else if (message instanceof EmoteNetwork.Rules rules) {
            LocalEmotes.rulesReceived(rules.rules());
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
        }
    }

    // replay world has no server to talk to
    static boolean canSend() {
        return networking != null && !EmotePlayers.inReplay() && networking.canSend();
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

        // sound belongs to the starter, both playing it would double it
        boolean withSound = player.equals(play.starter()) && play.options().sound() != null
                && (own ? config.playEmoteSounds : config.hearOtherSounds);
        EmotePlayback playback = EmotePlayers.startPartner(player, animation, play, link);
        Player source = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(player);
        if (withSound && source != null && isFresh(playback)) {
            EmoteSoundInstance.play(source, playback, play.options().sound(), own);
        }
    }

    // whether the emote started just now and not long ago (late viewer, or a jump in a replay)
    private static boolean isFresh(EmotePlayback playback) {
        float elapsed = EmotePlayers.time(0) - playback.startTime();
        return elapsed >= 0 && elapsed <= SOUND_START_WINDOW_TICKS;
    }

    // partner request to the local player, names are plain text
    public record PendingRequest(UUID starter, String starterName, String emoteName) {}

    // sends messages to the server, one per loader
    public interface ClientNetworking {

        boolean canSend();

        void send(byte[] message);
    }
}
