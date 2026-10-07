package com.yamikhal.playeremotes.client.compat;

//? if >=1.20.5 {
import com.yamikhal.playeremotes.client.animation.EmotePlayback;
import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.emote.ServerPackClient;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.network.EmotePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientGamePacketListener;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

// Flashback snapshots the world every few minutes of a recording, jumping in a replay starts from the nearest one
// before the target without the earlier messages. so every snapshot also gets the emote messages for what plays
// at that moment and the server pack manifest (see FlashbackRecorderMixin)
public final class FlashbackCompat {

    private FlashbackCompat() {}

    // called by Flashback's recorder on the client thread while writing a snapshot
    public static void writeSnapshot(Consumer<Packet<? super ClientGamePacketListener>> consumer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || EmotePlayers.inReplay()) {
            return;
        }

        EmoteNetwork.PackManifest manifest = ServerPackClient.manifest();
        if (manifest != null) {
            consumer.accept(packet(EmoteNetwork.packManifest(manifest.generation(), manifest.entries())));
        }

        long gameTime = minecraft.level.getGameTime();
        float now = EmotePlayers.time(0);
        Set<Integer> partnerships = new HashSet<>();
        for (Map.Entry<UUID, EmotePlayback> entry : EmotePlayers.playing().entrySet()) {
            EmotePlayback playback = entry.getValue();
            if (playback.isStopping()) {
                continue;
            }

            int elapsed = Math.max(0, Math.round(now - playback.startTime()));
            EmoteNetwork.PartnerPlay play = playback.partnerPlay();
            if (play == null) {
                consumer.accept(packet(EmoteNetwork.remotePlay(entry.getKey(), playback.animationId(), playback.options().withoutSound(),
                        elapsed, gameTime)));
            } else if (partnerships.add(play.id())) {
                // one message starts both players
                consumer.accept(packet(EmoteNetwork.partnerPlay(play.at(elapsed, gameTime))));
            }
        }
    }

    private static Packet<? super ClientGamePacketListener> packet(byte[] message) {
        return new ClientboundCustomPayloadPacket(new EmotePayload(EmotePayload.S2C, message));
    }
}
//?}
