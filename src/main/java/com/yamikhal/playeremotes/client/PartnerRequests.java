package com.yamikhal.playeremotes.client;

import com.yamikhal.playeremotes.client.animation.EmotePlayers;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.gui.Messages;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// partner emote requests to the local player, newest first, until accepted, withdrawn or expired, client thread only
final class PartnerRequests {

    private static final List<Entry> REQUESTS = new ArrayList<>();

    private PartnerRequests() {}

    static void received(EmoteNetwork.Request request) {
        remove(request.starter());
        // names come from other players' clients, so show them as plain text
        String starter = ChatFormatting.stripFormatting(request.starterName());
        Emote known = EmoteRegistry.get(request.emote());
        String emote = known != null ? known.name().getString() : ChatFormatting.stripFormatting(request.emoteName());
        PlayerEmotesClient.PendingRequest pending = new PlayerEmotesClient.PendingRequest(request.starter(), starter, emote);
        REQUESTS.add(0, new Entry(pending, EmotePlayers.time(0) + Math.max(1, request.seconds()) * 20F));
        announce(pending);
    }

    static void remove(UUID starter) {
        REQUESTS.removeIf(entry -> entry.request.starter().equals(starter));
    }

    static void clear() {
        REQUESTS.clear();
    }

    static void tick() {
        if (REQUESTS.isEmpty()) {
            return;
        }

        float now = EmotePlayers.time(0);
        REQUESTS.removeIf(entry -> now >= entry.expiresAt);
    }

    @Nullable
    static PlayerEmotesClient.PendingRequest latest() {
        return REQUESTS.isEmpty() ? null : REQUESTS.get(0).request;
    }

    // "X wants to emote with you: Hug [Accept]", the button runs /emoteaccept
    private static void announce(PlayerEmotesClient.PendingRequest request) {
        MutableComponent accept = Component.translatable("playeremotes.partner.accept_button")
                .withStyle(style -> style.withColor(ChatFormatting.GOLD).withClickEvent(acceptClick()));
        MutableComponent message = Component.translatable("playeremotes.partner.request",
                        Component.literal(request.starterName()).withStyle(ChatFormatting.GRAY),
                        Component.literal(request.emoteName()).withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.GRAY)
                .append(" ").append(accept);
        if (!PlayerEmotesClient.ACCEPT.isUnbound()) {
            message.append(Component.translatable("playeremotes.partner.accept_key", PlayerEmotesClient.ACCEPT.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GRAY));
        }

        Messages.show(message);
    }

    private static ClickEvent acceptClick() {
        //? if >=1.21.5 {
        /*return new ClickEvent.RunCommand("/emoteaccept");
        *///?} else
        return new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/emoteaccept");
    }

    private record Entry(PlayerEmotesClient.PendingRequest request, float expiresAt) {}
}
