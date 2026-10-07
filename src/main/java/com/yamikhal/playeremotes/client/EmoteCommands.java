package com.yamikhal.playeremotes.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.client.emote.Emote;
import com.yamikhal.playeremotes.client.emote.EmoteRegistry;
import com.yamikhal.playeremotes.client.gui.Messages;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

// /emote <emote>, /emoteaccept and /emotesync <player>, generic over the command source since it differs per
// loader, commands only use the client itself
public final class EmoteCommands {

    private static final int SUCCESS = 1;
    private static final int FAILURE = 0;

    private EmoteCommands() {}

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("emote")
                .then(RequiredArgumentBuilder.<S, String>argument("emote", StringArgumentType.greedyString())
                        .suggests(EmoteCommands::suggestEmotes)
                        .executes(context -> playEmote(StringArgumentType.getString(context, "emote")))));
        dispatcher.register(LiteralArgumentBuilder.<S>literal("emoteaccept").executes(context -> {
            LocalEmotes.accept();
            return SUCCESS;
        }));
        dispatcher.register(LiteralArgumentBuilder.<S>literal("emotesync")
                .then(RequiredArgumentBuilder.<S, String>argument("player", StringArgumentType.word())
                        .suggests(EmoteCommands::suggestEmotingPlayers)
                        .executes(context -> syncWith(StringArgumentType.getString(context, "player")))));
    }

    // finds an emote by full id, by path when unique, or by its (translated) name
    @Nullable
    static Emote findEmote(String input) {
        if (input.isEmpty()) {
            return null;
        }

        if (input.indexOf(':') >= 0) {
            ResourceLocation id = ResourceLocation.tryParse(input.toLowerCase(Locale.ROOT));
            return id == null ? null : EmoteRegistry.get(id);
        }

        String query = input.toLowerCase(Locale.ROOT);
        Emote byPath = null;
        Emote byName = null;
        int pathMatches = 0;
        for (Emote emote : EmoteRegistry.all()) {
            if (emote.id().getPath().equals(query)) {
                byPath = emote;
                pathMatches++;
            } else if (byName == null && emote.name().getString().toLowerCase(Locale.ROOT).equals(query)) {
                byName = emote;
            }
        }

        // prefer our own emote when namespaces share the path
        if (pathMatches > 1) {
            Emote own = EmoteRegistry.get(PlayerEmotes.id(query));
            if (own != null) {
                return own;
            }
        }

        return byPath != null ? byPath : byName;
    }

    private static int playEmote(String input) {
        Emote emote = findEmote(input.trim());
        if (emote == null) {
            Messages.show(Component.translatable("playeremotes.command.unknown_emote", input).withStyle(ChatFormatting.RED));
            return FAILURE;
        }

        PlayerEmotesClient.play(emote);
        return SUCCESS;
    }

    private static int syncWith(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        AbstractClientPlayer target = findPlayer(name);
        Component error = null;
        if (target == null) {
            error = Component.translatable("playeremotes.command.player_not_in_view", name);
        } else if (target == minecraft.player) {
            error = Component.translatable("playeremotes.command.sync_self");
        } else if (!PlayerEmotesClient.canSyncWith(target.getUUID())) {
            error = Component.translatable("playeremotes.command.not_emoting", target.getName());
        }

        if (error != null) {
            Messages.show(error.copy().withStyle(ChatFormatting.RED));
            return FAILURE;
        }

        PlayerEmotesClient.syncWith(target.getUUID());
        return SUCCESS;
    }

    @Nullable
    private static AbstractClientPlayer findPlayer(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return null;
        }

        for (AbstractClientPlayer player : minecraft.level.players()) {
            if (player.getScoreboardName().equalsIgnoreCase(name)) {
                return player;
            }
        }

        return null;
    }

    private static <S> CompletableFuture<Suggestions> suggestEmotes(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        List<String> paths = new ArrayList<>();
        for (Emote emote : EmoteRegistry.all()) {
            String path = emote.id().getPath();
            String full = emote.id().toString();
            if (path.startsWith(remaining)) {
                paths.add(path);
            } else if (full.startsWith(remaining)) {
                paths.add(full);
            }
        }

        paths.stream().distinct().sorted().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static <S> CompletableFuture<Suggestions> suggestEmotingPlayers(CommandContext<S> context, SuggestionsBuilder builder) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return builder.buildFuture();
        }

        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (AbstractClientPlayer player : minecraft.level.players()) {
            String name = player.getScoreboardName();
            if (player != minecraft.player && PlayerEmotesClient.canSyncWith(player.getUUID())
                    && name.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(name);
            }
        }

        return builder.buildFuture();
    }
}
