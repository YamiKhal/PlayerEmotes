package com.yamikhal.playeremotes.server;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.network.EmoteNetwork.Denial;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// server-side bookkeeping of running emotes. the server does not animate anything, it checks emotes against its rules,
// relays them to other players and replays running ones to players that start seeing the emoting player later. server
// thread only (the maps are concurrent for safety)
public final class EmoteTracker {

    // ticks a play may arrive early, so network jitter does not trip the cooldown the client also enforces
    private static final int COOLDOWN_TOLERANCE = 2;

    private static final Map<UUID, Running> RUNNING = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_PLAY = new ConcurrentHashMap<>();
    // players whose client has the mod and said hello
    private static final Set<UUID> HELLO = ConcurrentHashMap.newKeySet();

    private EmoteTracker() {}

    // the client's channel is up, tell it the rules and which server emote packs there are
    public static void hello(ServerPlayer player, boolean acceptsRequests) {
        boolean first = HELLO.add(player.getUUID());
        PartnerEmotes.setAcceptsRequests(player, acceptsRequests);
        sendRules(player);
        if (first) {
            ServerPacks.onHello(player);
        }
    }

    // called at the end of every server tick
    public static void tick(MinecraftServer server) {
        ServerPacks.tick(server);
        PartnerEmotes.tick(server);
        DevPartnerSetup.tick(server);
    }

    // sends the rules again to everyone, e.g. after the config was reloaded
    public static void resendRules(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (saidHello(player)) {
                sendRules(player);
            }
        }
    }

    public static void play(ServerPlayer player, int sequence, ResourceLocation emote, ResourceLocation animation,
                            EmoteNetwork.Options options) {
        Denial denial = check(player, emote);
        if (denial == null) {
            denial = checkCooldown(player);
        }

        if (denial != null) {
            deny(player, sequence, denial);
            return;
        }

        PartnerEmotes.leave(player);
        startRunning(player, emote, animation, options, now(player), 0);
    }

    // starts the emote target is playing for player, at the same point in time
    public static void sync(ServerPlayer player, int sequence, UUID target) {
        ServerConfig config = ServerConfig.get();
        Running running = RUNNING.get(target);
        ServerPlayer targetPlayer = level(player).getServer().getPlayerList().getPlayer(target);
        Denial denial = null;
        if (!config.syncEmotes || !EmotePermissions.has(player, EmotePermissions.Node.SYNC)) {
            denial = Denial.NOT_ALLOWED;
        } else if (running == null || running.partnership != 0 || PartnerEmotes.isBusy(target) || targetPlayer == null
                || targetPlayer == player || targetPlayer.level() != player.level()
                || targetPlayer.distanceToSqr(player) > viewDistanceSqr(player)) {
            denial = Denial.SYNC_FAILED;
        } else {
            denial = check(player, running.emote);
        }

        if (denial == null) {
            denial = checkCooldown(player);
        }

        if (denial != null) {
            deny(player, sequence, denial);
            return;
        }

        PartnerEmotes.leave(player);
        startRunning(player, running.emote, running.animation, running.options.withoutSound(), running.startTick, 0);
    }

    public static void stop(ServerPlayer player) {
        PartnerEmotes.leave(player);
        if (RUNNING.remove(player.getUUID()) != null) {
            broadcast(player, EmoteNetwork.remoteStop(player.getUUID()));
        }
    }

    // call when tracker starts receiving updates about target
    public static void onStartTracking(ServerPlayer tracker, Entity target) {
        Running running = RUNNING.get(target.getUUID());
        if (running == null || !(target instanceof ServerPlayer)) {
            return;
        }

        if (running.partnership != 0 && PartnerEmotes.replay(tracker, target.getUUID())) {
            return;
        }

        send(tracker, EmoteNetwork.remotePlay(target.getUUID(), running.animation, running.options, now(tracker) - running.startTick));
    }

    public static void onDisconnect(ServerPlayer player) {
        PartnerEmotes.onDisconnect(player);
        RUNNING.remove(player.getUUID());
        LAST_PLAY.remove(player.getUUID());
        HELLO.remove(player.getUUID());
        ServerPacks.onDisconnect(player);
    }

    // stops tracked emotes when the player changes dimension or respawns
    public static void onRespawnOrTeleport(ServerPlayer player) {
        stop(player);
    }

    static boolean saidHello(ServerPlayer player) {
        return HELLO.contains(player.getUUID());
    }

    // records a running emote, plain emotes (no partnership) are also sent to everyone else
    static void startRunning(ServerPlayer player, ResourceLocation emote, ResourceLocation animation, EmoteNetwork.Options options,
                             int startTick, int partnership) {
        RUNNING.put(player.getUUID(), new Running(emote, animation, options, startTick, partnership));
        LAST_PLAY.put(player.getUUID(), now(player));
        if (partnership == 0) {
            broadcast(player, EmoteNetwork.remotePlay(player.getUUID(), animation, options, now(player) - startTick));
        }
    }

    // forgets the emote of a partner emote that ended, clients learn about it from the end message
    static void forget(UUID player, int partnership) {
        Running running = RUNNING.get(player);
        if (running != null && running.partnership == partnership) {
            RUNNING.remove(player, running);
        }
    }

    // why the player may not play the emote, null if they may
    static Denial check(ServerPlayer player, ResourceLocation emote) {
        ServerConfig config = ServerConfig.get();
        if (!config.enabled) {
            return Denial.DISABLED;
        }

        if (!EmotePermissions.has(player, EmotePermissions.Node.USE) || config.isDisabled(emote)) {
            return Denial.NOT_ALLOWED;
        }

        if (config.isRestricted(emote) && !EmotePermissions.has(player, EmotePermissions.Node.RESTRICTED)) {
            return Denial.NOT_ALLOWED;
        }

        return null;
    }

    static Denial checkCooldown(ServerPlayer player) {
        Integer last = LAST_PLAY.get(player.getUUID());
        int cooldown = ServerConfig.get().cooldownTicks - COOLDOWN_TOLERANCE;
        int since = last == null ? Integer.MAX_VALUE : now(player) - last;
        return since >= 0 && since < cooldown ? Denial.COOLDOWN : null;
    }

    static void deny(ServerPlayer player, int sequence, Denial denial) {
        send(player, EmoteNetwork.denied(sequence, denial));
        // whatever ran before is replaced on the client, so the others stop seeing it too
        stop(player);
    }

    static int now(ServerPlayer player) {
        return level(player).getServer().getTickCount();
    }

    private static void sendRules(ServerPlayer player) {
        ServerConfig config = ServerConfig.get();
        boolean sync = config.enabled && config.syncEmotes && EmotePermissions.has(player, EmotePermissions.Node.SYNC);
        boolean partner = config.enabled && config.partnerEmotes && EmotePermissions.has(player, EmotePermissions.Node.PARTNER);
        send(player, EmoteNetwork.rules(new EmoteNetwork.ServerRules(config.cooldownTicks, sync, partner)));
    }

    private static double viewDistanceSqr(ServerPlayer player) {
        double blocks = (level(player).getServer().getPlayerList().getViewDistance() + 1) * 16.0;
        return blocks * blocks;
    }

    private static void broadcast(ServerPlayer source, byte[] message) {
        // everyone in the dimension, clients that do not have the player loaded ignore it and get a replay through
        // onStartTracking once the player comes into view
        for (ServerPlayer receiver : level(source).players()) {
            if (receiver != source) {
                send(receiver, message);
            }
        }
    }

    private static void send(ServerPlayer player, byte[] message) {
        PlayerEmotes.platform().sendToPlayer(player, message);
    }

    private static ServerLevel level(ServerPlayer player) {
        return (ServerLevel) player.level();
    }

    // partnership is the id of the partner emote this is part of, 0 for none
    private record Running(ResourceLocation emote, ResourceLocation animation, EmoteNetwork.Options options, int startTick,
                           int partnership) {}
}
