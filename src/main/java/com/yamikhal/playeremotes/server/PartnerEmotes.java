package com.yamikhal.playeremotes.server;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.network.EmoteNetwork.PartnerSpec;
import com.yamikhal.playeremotes.network.EmoteNetwork.StatusCode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

// two-player emotes. the starter plays an intro (holding its last frame) and waits. a request goes to the nearest player
// in front of them who faces them back, if there is none, or that player does not answer in time or leaves, anyone who
// walks up and accepts joins instead. once joined both play their part, moving, getting hurt, attacking, dying or leaving
// ends it for both. server thread only
public final class PartnerEmotes {

    // farthest two players may stand apart to start, in blocks (horizontally)
    private static final double RANGE = 2.0;
    private static final double MAX_HEIGHT_DIFFERENCE = 0.6;
    // widest angle between where a player looks and the other player for them to count as facing each other
    private static final double MAX_FACING_ANGLE = 60;
    // how far a waiting or partnered player may drift (e.g. pushed by water) before the emote ends
    private static final double MAX_DRIFT = 0.35;
    // a starter asking the same player again within this many ticks does not show them a new chat message
    private static final int REQUEST_REPEAT_TICKS = 100;

    private static final Map<UUID, Waiting> WAITING = new HashMap<>();
    private static final Map<UUID, Partnership> ACTIVE = new HashMap<>();
    // players who turned partner requests off
    private static final Set<UUID> NO_REQUESTS = new HashSet<>();
    // who each starter last sent a request to, and when, against request spam
    private static final Map<UUID, LastRequest> LAST_REQUEST = new HashMap<>();

    private static int nextId = 1;

    private PartnerEmotes() {}

    public static void start(ServerPlayer player, int sequence, ResourceLocation emote, PartnerSpec spec,
                             EmoteNetwork.Options options, String name) {
        EmoteNetwork.Denial denial = allowed(player) ? EmoteTracker.check(player, emote) : EmoteNetwork.Denial.NOT_ALLOWED;
        if (denial == null) {
            denial = EmoteTracker.checkCooldown(player);
        }

        if (denial != null) {
            EmoteTracker.deny(player, sequence, denial);
            return;
        }

        leave(player);
        // everyone sees the intro like any other emote
        EmoteTracker.startRunning(player, emote, spec.intro(), options, EmoteTracker.now(player), 0);
        Waiting waiting = new Waiting(player.getUUID(), emote, spec, options, name, player.position(), null, 0);
        WAITING.put(player.getUUID(), waiting);

        ServerPlayer target = nearest(player, candidate -> !NO_REQUESTS.contains(candidate.getUUID()) && !isBusy(candidate.getUUID()));
        if (target != null) {
            sendRequest(player, waiting, target);
        } else {
            status(player, StatusCode.WAITING_OPEN, "");
        }
    }

    // accepts a request to the player, or joins the open partner emote of the nearest player in front
    public static void accept(ServerPlayer player) {
        if (!allowed(player)) {
            status(player, StatusCode.NOT_ALLOWED, "");
            return;
        }

        Waiting chosen = null;
        Waiting tooFar = null;
        double best = Double.MAX_VALUE;
        for (Waiting waiting : WAITING.values()) {
            boolean direct = player.getUUID().equals(waiting.recipient);
            if (!direct && waiting.recipient != null) continue;

            ServerPlayer starter = player(player, waiting.starter);
            if (starter == null || starter == player) continue;

            if (!facingEachOther(starter, player)) {
                if (direct) {
                    tooFar = waiting;
                }

                continue;
            }

            double distance = starter.distanceToSqr(player);
            // a request to this player beats an open emote at the same spot
            if (direct) {
                distance -= 0.01;
            }

            if (distance < best) {
                best = distance;
                chosen = waiting;
            }
        }

        if (chosen != null) {
            ServerPlayer starter = player(player, chosen.starter);
            if (EmoteTracker.check(player, chosen.emote) != null) {
                status(player, StatusCode.NOT_ALLOWED, "");
            } else if (starter != null) {
                join(chosen, starter, player);
            }
        } else if (tooFar != null) {
            ServerPlayer starter = player(player, tooFar.starter);
            status(player, StatusCode.GET_CLOSER, starter == null ? "" : starter.getScoreboardName());
        } else {
            status(player, StatusCode.NO_PARTNER, "");
        }
    }

    static void setAcceptsRequests(ServerPlayer player, boolean accepts) {
        if (accepts) {
            NO_REQUESTS.remove(player.getUUID());
        } else {
            NO_REQUESTS.add(player.getUUID());
        }
    }

    static boolean isBusy(UUID player) {
        return WAITING.containsKey(player) || ACTIVE.containsKey(player);
    }

    // the player stopped, started something else, left or died: end whatever partner emote they are in
    static void leave(ServerPlayer player) {
        Waiting waiting = WAITING.remove(player.getUUID());
        if (waiting != null && waiting.recipient != null) {
            ServerPlayer recipient = player(player, waiting.recipient);
            if (recipient != null) {
                send(recipient, EmoteNetwork.requestCancel(waiting.starter));
            }
        }

        Partnership partnership = ACTIVE.get(player.getUUID());
        if (partnership != null) {
            end(player.level().getServer(), partnership);
        }
    }

    static void onDisconnect(ServerPlayer player) {
        leave(player);
        NO_REQUESTS.remove(player.getUUID());
        LAST_REQUEST.remove(player.getUUID());
        // requests to the player become open to anyone
        for (Map.Entry<UUID, Waiting> entry : WAITING.entrySet()) {
            if (player.getUUID().equals(entry.getValue().recipient)) {
                entry.setValue(entry.getValue().withRecipient(null, 0));
                ServerPlayer starter = player(player, entry.getKey());
                if (starter != null) {
                    status(starter, StatusCode.PARTNER_LEFT, player.getScoreboardName());
                }
            }
        }
    }

    // sends a running partner emote to a player who starts seeing one of the two
    static boolean replay(ServerPlayer tracker, UUID target) {
        Partnership partnership = ACTIVE.get(target);
        if (partnership == null) {
            return false;
        }

        EmoteNetwork.PartnerPlay play = partnership.play;
        int elapsed = EmoteTracker.now(tracker) - partnership.startTick;
        send(tracker, EmoteNetwork.partnerPlay(new EmoteNetwork.PartnerPlay(play.id(), play.starter(), play.partner(), play.x(),
                play.y(), play.z(), play.yaw(), play.distance(), play.starterAnimation(), play.partnerAnimation(), play.options(), elapsed)));
        return true;
    }

    static void tick(MinecraftServer server) {
        if (WAITING.isEmpty() && ACTIVE.isEmpty()) {
            return;
        }

        int now = server.getTickCount();
        List<UUID> cancelled = new ArrayList<>();
        for (Map.Entry<UUID, Waiting> entry : WAITING.entrySet()) {
            Waiting waiting = entry.getValue();
            ServerPlayer starter = server.getPlayerList().getPlayer(waiting.starter);
            if (starter == null || !starter.isAlive() || starter.position().distanceToSqr(waiting.position) > MAX_DRIFT * MAX_DRIFT) {
                cancelled.add(entry.getKey());
            } else if (waiting.recipient != null && now - waiting.expiresAt >= 0) {
                ServerPlayer recipient = server.getPlayerList().getPlayer(waiting.recipient);
                if (recipient != null) {
                    send(recipient, EmoteNetwork.requestCancel(waiting.starter));
                }

                entry.setValue(waiting.withRecipient(null, 0));
                status(starter, StatusCode.REQUEST_EXPIRED, recipient == null ? "" : recipient.getScoreboardName());
            }
        }

        for (UUID id : cancelled) {
            ServerPlayer starter = server.getPlayerList().getPlayer(id);
            if (starter != null) {
                EmoteTracker.stop(starter);
            } else {
                WAITING.remove(id);
            }
        }

        List<Partnership> ended = new ArrayList<>();
        for (Partnership partnership : new HashSet<>(ACTIVE.values())) {
            ServerPlayer starter = server.getPlayerList().getPlayer(partnership.starter);
            ServerPlayer partner = server.getPlayerList().getPlayer(partnership.partner);
            if (starter == null || partner == null || !starter.isAlive() || !partner.isAlive() || starter.level() != partner.level()
                    || starter.position().distanceToSqr(partnership.starterPosition) > MAX_DRIFT * MAX_DRIFT
                    || partner.position().distanceToSqr(partnership.partnerPosition) > MAX_DRIFT * MAX_DRIFT) {
                ended.add(partnership);
            }
        }

        for (Partnership partnership : ended) {
            end(server, partnership);
        }
    }

    private static void join(Waiting waiting, ServerPlayer starter, ServerPlayer partner) {
        WAITING.remove(waiting.starter);
        leave(partner);
        // facing from the starter to the partner, in Minecraft's yaw (0 = +Z, 90 = -X)
        double dx = partner.getX() - starter.getX();
        double dz = partner.getZ() - starter.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        int now = EmoteTracker.now(starter);
        EmoteNetwork.PartnerPlay play = new EmoteNetwork.PartnerPlay(nextId++, starter.getUUID(), partner.getUUID(),
                starter.getX(), starter.getY(), starter.getZ(), yaw, waiting.spec.distance(), waiting.spec.action(),
                waiting.spec.partnerAction(), waiting.options, 0);
        Partnership partnership = new Partnership(play.id(), starter.getUUID(), partner.getUUID(), starter.position(),
                partner.position(), play, now);
        ACTIVE.put(starter.getUUID(), partnership);
        ACTIVE.put(partner.getUUID(), partnership);
        EmoteTracker.startRunning(starter, waiting.emote, waiting.spec.action(), waiting.options, now, play.id());
        EmoteTracker.startRunning(partner, waiting.emote, waiting.spec.partnerAction(), waiting.options, now, play.id());
        byte[] message = EmoteNetwork.partnerPlay(play);
        for (ServerPlayer receiver : level(starter).players()) {
            send(receiver, message);
        }
    }

    private static void end(MinecraftServer server, Partnership partnership) {
        ACTIVE.remove(partnership.starter, partnership);
        ACTIVE.remove(partnership.partner, partnership);
        EmoteTracker.forget(partnership.starter, partnership.id);
        EmoteTracker.forget(partnership.partner, partnership.id);
        byte[] message = EmoteNetwork.partnerEnd(partnership.id, partnership.starter, partnership.partner);
        // everyone who may see either of the two, which may be in different dimensions by now
        for (ServerPlayer receiver : server.getPlayerList().getPlayers()) {
            send(receiver, message);
        }
    }

    private static void sendRequest(ServerPlayer starter, Waiting waiting, ServerPlayer target) {
        int seconds = ServerConfig.get().partnerRequestSeconds;
        int now = EmoteTracker.now(starter);
        WAITING.put(starter.getUUID(), waiting.withRecipient(target.getUUID(), now + seconds * 20));
        LastRequest last = LAST_REQUEST.get(starter.getUUID());
        if (last == null || !last.recipient.equals(target.getUUID()) || now - last.tick >= REQUEST_REPEAT_TICKS || now < last.tick) {
            LAST_REQUEST.put(starter.getUUID(), new LastRequest(target.getUUID(), now));
            send(target, EmoteNetwork.request(starter.getUUID(), starter.getScoreboardName(), waiting.emote, waiting.name, seconds));
        }

        status(starter, StatusCode.REQUEST_SENT, target.getScoreboardName());
    }

    // the nearest player in front of player who faces them back and passes the filter
    @Nullable
    private static ServerPlayer nearest(ServerPlayer player, Predicate<ServerPlayer> filter) {
        ServerPlayer best = null;
        double bestDistance = Double.MAX_VALUE;
        for (ServerPlayer other : level(player).players()) {
            if (other == player || !EmoteTracker.saidHello(other) || !filter.test(other) || !facingEachOther(player, other)) continue;

            double distance = other.distanceToSqr(player);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }

        return best;
    }

    private static boolean facingEachOther(ServerPlayer a, ServerPlayer b) {
        if (a.level() != b.level() || !a.isAlive() || !b.isAlive() || a.isSpectator() || b.isSpectator()) {
            return false;
        }

        double dx = b.getX() - a.getX();
        double dz = b.getZ() - a.getZ();
        if (dx * dx + dz * dz > RANGE * RANGE || Math.abs(b.getY() - a.getY()) > MAX_HEIGHT_DIFFERENCE) {
            return false;
        }

        return looksToward(a, dx, dz) && looksToward(b, -dx, -dz);
    }

    // whether the player's view points within MAX_FACING_ANGLE of the horizontal direction
    private static boolean looksToward(ServerPlayer player, double dx, double dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        // standing on the same spot counts as facing
        if (length < 1e-3) {
            return true;
        }

        double yaw = Math.toRadians(player.getYRot());
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);
        double cos = (lookX * dx + lookZ * dz) / length;
        return cos >= Math.cos(Math.toRadians(MAX_FACING_ANGLE));
    }

    private static boolean allowed(ServerPlayer player) {
        ServerConfig config = ServerConfig.get();
        return config.enabled && config.partnerEmotes && EmotePermissions.has(player, EmotePermissions.Node.PARTNER);
    }

    private static void status(ServerPlayer player, StatusCode code, String name) {
        send(player, EmoteNetwork.status(code, name));
    }

    @Nullable
    private static ServerPlayer player(ServerPlayer any, UUID id) {
        return any.level().getServer().getPlayerList().getPlayer(id);
    }

    private static ServerLevel level(ServerPlayer player) {
        return (ServerLevel) player.level();
    }

    private static void send(ServerPlayer player, byte[] message) {
        PlayerEmotes.platform().sendToPlayer(player, message);
    }

    private record Waiting(UUID starter, ResourceLocation emote, PartnerSpec spec, EmoteNetwork.Options options,
                           String name, Vec3 position, @Nullable UUID recipient, int expiresAt) {

        Waiting withRecipient(@Nullable UUID player, int expires) {
            return new Waiting(this.starter, this.emote, this.spec, this.options, this.name, this.position, player, expires);
        }
    }

    private record Partnership(int id, UUID starter, UUID partner, Vec3 starterPosition, Vec3 partnerPosition,
                               EmoteNetwork.PartnerPlay play, int startTick) {}

    private record LastRequest(UUID recipient, int tick) {}
}
