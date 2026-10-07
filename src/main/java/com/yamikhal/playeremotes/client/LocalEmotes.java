package com.yamikhal.playeremotes.client;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.animation.AnimationRegistry;
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.animation.EmoteSoundInstance;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.emote.ServerPackClient;
import com.yamikhal.playeremotes.client.gui.Messages;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

// local player's emotes: start and stop (played locally right away, then told to the server), what cancels
// them, camera while emoting and the server's rules. in a replay the local player is only a camera and cannot
// emote, client thread only
final class LocalEmotes {

    // whether the server knows about a local emote that is not stopped yet
    private static boolean running;
    // numbers the emotes sent to the server, so a late denial cannot stop a newer emote
    private static int sequence;
    // partner emote the local player is in, 0 for none
    private static int partnership;
    // when the last emote started, in client ticks, for the server's cooldown
    private static float lastStart = Float.NEGATIVE_INFINITY;
    // whether the camera went third person for the running emote, and in which level
    private static boolean cameraSwitched;
    @Nullable
    private static Object cameraLevel;

    // connection the hello went out on, a new connection needs a new hello
    @Nullable
    private static Object helloConnection;
    private static EmoteNetwork.ServerRules rules = EmoteNetwork.ServerRules.UNKNOWN;

    private LocalEmotes() {}

    static int partnership() {
        return partnership;
    }

    static EmoteNetwork.ServerRules rules() {
        return rules;
    }

    // forgets the previous server once on another one (or a replay), says hello to the new one
    static void syncConnection(Minecraft minecraft) {
        Object connection = minecraft.getConnection();
        boolean replay = EmotePlayers.inReplay();
        if (connection != helloConnection && (connection == null || replay || PlayerEmotesClient.canSend())) {
            helloConnection = connection;
            PlayerEmotesClient.config().tickSave(true);
            rules = EmoteNetwork.ServerRules.UNKNOWN;
            running = false;
            partnership = 0;
            ServerPackClient.clear();
            PartnerRequests.clear();
            if (connection != null && !replay) {
                sendHello();
            }
        }
    }

    static void tick(Minecraft minecraft) {
        syncConnection(minecraft);
        PartnerRequests.tick();
        PlayerEmotesClient.config().tickSave(false);

        LocalPlayer player = minecraft.player;
        if (player == null || EmotePlayers.inReplay()) {
            cameraSwitched = false;
            return;
        }

        boolean playing = EmotePlayers.isPlaying(player.getUUID());
        if (playing && shouldCancel(minecraft, player)) {
            stop();
            playing = false;
        } else if (!playing && running) {
            // emote ended on its own, let the server forget it
            running = false;
            PlayerEmotesClient.send(EmoteNetwork.stopRequest());
        }

        if (!playing || minecraft.level != cameraLevel) {
            restoreCamera(minecraft);
        }
    }

    static void play(Emote emote) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || EmotePlayers.inReplay() || !checkCooldown()) {
            return;
        }

        if (emote.partner() != null) {
            startPartner(minecraft, player, emote);
            return;
        }

        ResourceLocation animation = EmoteRegistry.pickAnimation(emote, minecraft);
        if (animation == null) {
            PlayerEmotes.LOGGER.warn("Emote {} has no loaded animation", emote.id());
            return;
        }

        EmoteNetwork.Options options = new EmoteNetwork.Options(emote.look(), emote.splitLimbs(), emote.blendInTicks(), emote.blendOutTicks(),
                PlayerEmotesClient.config().playEmoteSounds ? emote.sound() : null, emote.prop(), emote.props());
        EmotePlayback playback = EmotePlayers.start(player.getUUID(), emote.id(), animation, options, 0);
        if (options.sound() != null) {
            EmoteSoundInstance.play(player, playback, options.sound(), true);
        }

        started(minecraft);
        PlayerEmotesClient.send(EmoteNetwork.playRequest(sequence, emote.id(), animation, options));
        PlayerEmotesClient.config().markRecent(emote.id());
        PlayerEmotesClient.config().saveSoon();
    }

    // accepts the newest partner request, or joins the waiting player in front
    static void accept() {
        if (EmotePlayers.inReplay()) {
            return;
        }

        if (!rules.partner()) {
            Messages.overlay(Component.translatable("playeremotes.partner.status.not_allowed").withStyle(ChatFormatting.RED));
            return;
        }

        PlayerEmotesClient.send(EmoteNetwork.accept());
    }

    // local player is one of the two of a partner emote that just started
    static void partnerStarted(int id) {
        partnership = id;
        running = true;
        lastStart = EmotePlayers.time(0);
        switchCamera(Minecraft.getInstance());
    }

    // server ended the partner emote, already stopped for both so nothing to send
    static void partnerEnded(int id) {
        if (partnership != id) {
            return;
        }

        partnership = 0;
        running = false;
    }

    // tells the server the preferences it needs (and asks for its rules)
    static void sendHello() {
        PlayerEmotesClient.send(EmoteNetwork.hello(PlayerEmotesClient.config().acceptRequests));
    }

    // whether syncWith can join the other player's emote, not one this client cannot show (yet)
    static boolean canSyncWith(UUID other) {
        EmotePlayback playback = EmotePlayers.get(other);
        return rules.sync() && playback != null && !playback.isStopping() && playback.link() == null
                && AnimationRegistry.get(playback.animationId()) != null;
    }

    // joins the emote another player plays, at the same point in it
    static void syncWith(UUID other) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        EmotePlayback target = EmotePlayers.get(other);
        if (player == null || target == null || target.isStopping() || AnimationRegistry.get(target.animationId()) == null
                || EmotePlayers.inReplay() || !checkCooldown()) {
            return;
        }

        int elapsed = Math.max(0, Math.round(EmotePlayers.time(0) - target.startTime()));
        EmotePlayers.start(player.getUUID(), target.emoteId(), target.animationId(), target.options().withoutSound(), elapsed);
        started(minecraft);
        // server starts it from the other player's start, so everyone sees both in step
        PlayerEmotesClient.send(EmoteNetwork.syncRequest(sequence, other));
    }

    static void stop() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        partnership = 0;
        EmotePlayers.stop(minecraft.player.getUUID());
        if (running) {
            running = false;
            PlayerEmotesClient.send(EmoteNetwork.stopRequest());
        }
    }

    // server refused one of our emotes
    static void denied(int deniedSequence, EmoteNetwork.Denial reason) {
        if (deniedSequence != sequence) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            EmotePlayers.stop(minecraft.player.getUUID());
        }

        running = false;
        String key = "playeremotes.message.denied." + reason.name().toLowerCase(Locale.ROOT);
        Messages.overlay(Component.translatable(key).withStyle(ChatFormatting.RED));
    }

    static void rulesReceived(EmoteNetwork.ServerRules received) {
        rules = received;
    }

    // plays the partner emote intro and asks the server to find a partner
    private static void startPartner(Minecraft minecraft, LocalPlayer player, Emote emote) {
        EmoteNetwork.PartnerSpec spec = emote.partner();
        if (!rules.partner()) {
            Messages.overlay(Component.translatable("playeremotes.partner.status.not_allowed").withStyle(ChatFormatting.RED));
            return;
        }

        EmoteNetwork.Options options = new EmoteNetwork.Options(emote.look(), emote.splitLimbs(), emote.blendInTicks(), emote.blendOutTicks(),
                PlayerEmotesClient.config().playEmoteSounds ? emote.sound() : null, emote.prop(), emote.props());
        EmotePlayers.start(player.getUUID(), emote.id(), spec.intro(), options.withoutSound(), 0);
        partnership = 0;
        started(minecraft);
        PlayerEmotesClient.send(EmoteNetwork.partnerStart(sequence, emote.id(), spec, options, emote.name().getString()));
        PlayerEmotesClient.config().markRecent(emote.id());
        PlayerEmotesClient.config().saveSoon();
    }

    private static void started(Minecraft minecraft) {
        sequence++;
        partnership = 0;
        running = true;
        lastStart = EmotePlayers.time(0);
        switchCamera(minecraft);
    }

    // refuses emotes the server would refuse for coming too soon
    private static boolean checkCooldown() {
        if (EmotePlayers.time(0) - lastStart >= rules.cooldownTicks()) {
            return true;
        }

        Messages.overlay(Component.translatable("playeremotes.message.denied.cooldown").withStyle(ChatFormatting.RED));
        return false;
    }

    private static void switchCamera(Minecraft minecraft) {
        if (!PlayerEmotesClient.config().thirdPersonEmotes || cameraSwitched
                || minecraft.options.getCameraType() != CameraType.FIRST_PERSON) {
            return;
        }

        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        cameraSwitched = true;
        cameraLevel = minecraft.level;
    }

    // back to first person, unless the player picked another view while emoting
    private static void restoreCamera(Minecraft minecraft) {
        if (!cameraSwitched) {
            return;
        }

        cameraSwitched = false;
        if (minecraft.options.getCameraType() == CameraType.THIRD_PERSON_BACK) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
    }

    private static boolean isSwinging(LocalPlayer player) {
        //? if >=26.3 {
        /*return player.getCurrentSwing() != null;
        *///?} else
        return player.swinging;
    }

    private static boolean shouldCancel(Minecraft minecraft, LocalPlayer player) {
        Options options = minecraft.options;
        return options.keyUp.isDown() || options.keyDown.isDown() || options.keyLeft.isDown() || options.keyRight.isDown()
                || options.keyJump.isDown() || player.isShiftKeyDown() || options.keyAttack.isDown() || isSwinging(player)
                || player.isPassenger() || player.isSwimming() || player.isFallFlying() || player.isUsingItem()
                || player.hurtTime > 0 || !player.isAlive();
    }
}
