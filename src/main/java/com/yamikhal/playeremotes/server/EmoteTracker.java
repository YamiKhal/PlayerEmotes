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

// server side bookkeeping of running emotes. server animates nothing, it checks emotes against its rules, relays
// them to players around and replays running ones to players that start seeing the emoting player later.
// server thread only (maps are concurrent for safety)
public final class EmoteTracker {

    // ticks a play may arrive early, so network jitter does not trip the cooldown the client also checks
    private static final int COOLDOWN_TOLERANCE = 2;

    private static final Map<UUID, Running> RUNNING = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> LAST_PLAY = new ConcurrentHashMap<>();
    // players whose client has the mod and said hello
    private static final Set<UUID> HELLO = ConcurrentHashMap.newKeySet();

    private EmoteTracker() {}

    // client channel is up, send it the rules and which server emote packs there are
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

    // sends the rules to everyone again, e.g. after a config reload
    public static void resendRules(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            if (saidHello(player)) {
                sendRules(player);
            }
        }
    }

    public static void play(ServerPlayer player, int sequence, ResourceLocation emote, ResourceLocation animation,
                            EmoteNetwork.Options options) {
        Denial denial = check(player, emote, animation);
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

    // starts the emote target plays for player, at the same point in time
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
            denial = check(player, running.emote, running.animation);
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
            broadcast(player, EmoteNetwork.remoteStop(player.getUUID(), gameTime(player)));
        }
    }

    // call when tracker starts getting updates about target
    public static void onStartTracking(ServerPlayer tracker, Entity target) {
        Running running = RUNNING.get(target.getUUID());
        if (running == null || !(target instanceof ServerPlayer)) {
            return;
        }

        if (running.partnership != 0 && PartnerEmotes.replay(tracker, target.getUUID())) {
            return;
        }

        send(tracker, EmoteNetwork.remotePlay(target.getUUID(), running.animation, running.options, now(tracker) - running.startTick,
                gameTime(tracker)));
    }

    public static void onDisconnect(ServerPlayer player) {
        PartnerEmotes.onDisconnect(player);
        RUNNING.remove(player.getUUID());
        LAST_PLAY.remove(player.getUUID());
        HELLO.remove(player.getUUID());
        ServerPacks.onDisconnect(player);
    }

    // respawned player starts standing. dimension change needs nothing here, client drops its emote with the old
    // world and tells the server
    public static void onRespawn(ServerPlayer player) {
        stop(player);
    }

    static boolean saidHello(ServerPlayer player) {
        return HELLO.contains(player.getUUID());
    }

    // records a running emote, plain emotes (no partnership) also go to everyone
    static void startRunning(ServerPlayer player, ResourceLocation emote, ResourceLocation animation, EmoteNetwork.Options options,
                             int startTick, int partnership) {
        RUNNING.put(player.getUUID(), new Running(emote, animation, options, startTick, partnership));
        LAST_PLAY.put(player.getUUID(), now(player));
        if (partnership == 0) {
            broadcast(player, EmoteNetwork.remotePlay(player.getUUID(), animation, options, now(player) - startTick, gameTime(player)));
        }
    }

    // forgets the emote of an ended partner emote, clients learn it from the end message
    static void forget(UUID player, int partnership) {
        Running running = RUNNING.get(player);
        if (running != null && running.partnership == partnership) {
            RUNNING.remove(player, running);
        }
    }

    // why the player may not play the emote, null if they may. the animations it plays count too, the client names
    // both, so an allowed emote's name cannot carry a disabled or restricted emote's animation
    static Denial check(ServerPlayer player, ResourceLocation emote, ResourceLocation... animations) {
        ServerConfig config = ServerConfig.get();
        if (!config.enabled) {
            return Denial.DISABLED;
        }

        if (!EmotePermissions.has(player, EmotePermissions.Node.USE) || !allowed(player, config, emote)) {
            return Denial.NOT_ALLOWED;
        }

        for (ResourceLocation animation : animations) {
            if (!animation.equals(emote) && !allowed(player, config, animation)) {
                return Denial.NOT_ALLOWED;
            }
        }

        return null;
    }

    private static boolean allowed(ServerPlayer player, ServerConfig config, ResourceLocation id) {
        return !config.isDisabled(id) && (!config.isRestricted(id) || EmotePermissions.has(player, EmotePermissions.Node.RESTRICTED));
    }

    static Denial checkCooldown(ServerPlayer player) {
        Integer last = LAST_PLAY.get(player.getUUID());
        int cooldown = ServerConfig.get().cooldownTicks - COOLDOWN_TOLERANCE;
        int since = last == null ? Integer.MAX_VALUE : now(player) - last;
        return since >= 0 && since < cooldown ? Denial.COOLDOWN : null;
    }

    static void deny(ServerPlayer player, int sequence, Denial denial) {
        send(player, EmoteNetwork.denied(sequence, denial));
        // whatever ran before gets replaced on the client, so others stop seeing it too
        stop(player);
    }

    static int now(ServerPlayer player) {
        return level(player).getServer().getTickCount();
    }

    // game time clients see (same in every dimension), stamped on messages for replays
    static long gameTime(ServerPlayer player) {
        return level(player).getServer().overworld().getGameTime();
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

    // players that may have source loaded: entity tracking never reaches past the view distance, players further away
    // get a replay through onStartTracking once source comes into view. source gets it too, its client already plays
    // the emote and ignores it, but replay mods record what arrives, so this puts the player's own emotes into recordings
    static void broadcast(ServerPlayer source, byte[] message) {
        double range = viewDistanceSqr(source);
        for (ServerPlayer receiver : level(source).players()) {
            double dx = receiver.getX() - source.getX();
            double dz = receiver.getZ() - source.getZ();
            if (receiver == source || dx * dx + dz * dz <= range) {
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

    // partnership is the id of the partner emote this belongs to, 0 for none
    private record Running(ResourceLocation emote, ResourceLocation animation, EmoteNetwork.Options options, int startTick,
                           int partnership) {}
}
