package com.yamikhal.playeremotes.network;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.server.EmoteTracker;
import com.yamikhal.playeremotes.server.PartnerEmotes;
import com.yamikhal.playeremotes.server.ServerPacks;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

// wire format of all emote messages. loaders only move opaque byte arrays over one channel per direction (C2S, S2C),
// everything protocol related lives here. every message starts with the protocol version and a message type, so the
// format can evolve without breaking the loader glue
public final class EmoteNetwork {

    public static final ResourceLocation C2S = PlayerEmotes.id("c2s");
    public static final ResourceLocation S2C = PlayerEmotes.id("s2c");

    public static final int PROTOCOL = 3;
    private static final int MAX_ID_LENGTH = 256;

    // client -> server
    private static final int C2S_PLAY = 0;
    private static final int C2S_STOP = 1;
    private static final int C2S_HELLO = 2;
    private static final int C2S_SYNC = 3;
    private static final int C2S_PACKS_REQUEST = 4;
    private static final int C2S_PARTNER_START = 5;
    private static final int C2S_ACCEPT = 6;

    // server -> client
    private static final int S2C_PLAY = 0;
    private static final int S2C_STOP = 1;
    private static final int S2C_DENIED = 2;
    private static final int S2C_CONFIG = 3;
    private static final int S2C_PACKS_MANIFEST = 4;
    private static final int S2C_PACKS_DATA = 5;
    private static final int S2C_PARTNER_PLAY = 6;
    private static final int S2C_PARTNER_END = 7;
    private static final int S2C_REQUEST = 8;
    private static final int S2C_REQUEST_CANCEL = 9;
    private static final int S2C_STATUS = 10;
    // longest emote name shown in a partner request
    private static final int MAX_NAME_LENGTH = 64;

    // limits for server emote packs, enforced on both sides
    public static final int MAX_PACK_FILES = 1024;
    public static final int MAX_PACK_FILE_SIZE = 2 * 1024 * 1024;
    public static final int MAX_PACK_TOTAL_SIZE = 16 * 1024 * 1024;
    // compressed bytes per data message, well below every loader's payload limit
    public static final int PACK_CHUNK_SIZE = 32 * 1024;
    private static final int MAX_PATH_LENGTH = 256;
    private static final int SHA1_LENGTH = 20;

    private EmoteNetwork() {}

    // sequence numbers the local player's emotes so a late denial cannot stop a newer emote, emote is the one that was
    // picked and is checked against the server's rules
    public static byte[] playRequest(int sequence, ResourceLocation emote, ResourceLocation animation, Options options) {
        return encode(C2S_PLAY, buf -> {
            buf.writeVarInt(sequence);
            buf.writeUtf(emote.toString(), MAX_ID_LENGTH);
            buf.writeUtf(animation.toString(), MAX_ID_LENGTH);
            options.write(buf);
        });
    }

    public static byte[] stopRequest() {
        return encode(C2S_STOP, buf -> {});
    }

    // sent once the channel is up and whenever the player's preferences change, the server answers with its rules
    public static byte[] hello(boolean acceptRequests) {
        return encode(C2S_HELLO, buf -> buf.writeBoolean(acceptRequests));
    }

    // starts a two-player emote: plays the intro and waits for a partner
    public static byte[] partnerStart(int sequence, ResourceLocation emote, PartnerSpec spec, Options options, String name) {
        return encode(C2S_PARTNER_START, buf -> {
            buf.writeVarInt(sequence);
            buf.writeUtf(emote.toString(), MAX_ID_LENGTH);
            spec.write(buf);
            options.write(buf);
            buf.writeUtf(truncate(name), MAX_NAME_LENGTH * 4);
        });
    }

    // accepts a partner request, or joins the waiting player in front
    public static byte[] accept() {
        return encode(C2S_ACCEPT, buf -> {});
    }

    // joins the emote another player is playing, in step with them
    public static byte[] syncRequest(int sequence, UUID target) {
        return encode(C2S_SYNC, buf -> {
            buf.writeVarInt(sequence);
            buf.writeUUID(target);
        });
    }

    // asks for the files of a server pack manifest that are not in the client's cache
    public static byte[] packsRequest(int generation, int[] indices) {
        return encode(C2S_PACKS_REQUEST, buf -> {
            buf.writeVarInt(generation);
            buf.writeVarInt(indices.length);
            for (int index : indices) {
                buf.writeVarInt(index);
            }
        });
    }

    // must be called on the server thread
    public static void handleServer(ServerPlayer player, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            if (buf.readUnsignedByte() != PROTOCOL) {
                return;
            }

            switch (buf.readUnsignedByte()) {
                case C2S_PLAY -> {
                    int sequence = buf.readVarInt();
                    ResourceLocation emote = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
                    ResourceLocation animation = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
                    Options options = Options.read(buf);
                    if (emote != null && animation != null) {
                        EmoteTracker.play(player, sequence, emote, animation, options);
                    }
                }
                case C2S_STOP -> EmoteTracker.stop(player);
                case C2S_HELLO -> EmoteTracker.hello(player, !buf.isReadable() || buf.readBoolean());
                case C2S_SYNC -> EmoteTracker.sync(player, buf.readVarInt(), buf.readUUID());
                case C2S_PARTNER_START -> {
                    int sequence = buf.readVarInt();
                    ResourceLocation emote = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
                    PartnerSpec spec = PartnerSpec.read(buf);
                    Options options = Options.read(buf);
                    String name = truncate(buf.readUtf(MAX_NAME_LENGTH * 4));
                    if (emote != null && spec != null) {
                        PartnerEmotes.start(player, sequence, emote, spec, options, name);
                    }
                }
                case C2S_ACCEPT -> PartnerEmotes.accept(player);
                case C2S_PACKS_REQUEST -> {
                    int generation = buf.readVarInt();
                    int count = buf.readVarInt();
                    if (count < 0 || count > MAX_PACK_FILES) {
                        return;
                    }

                    int[] indices = new int[count];
                    for (int i = 0; i < count; i++) {
                        indices[i] = buf.readVarInt();
                    }

                    ServerPacks.request(player, generation, indices);
                }
                default -> {}
            }
        } catch (RuntimeException e) {
            PlayerEmotes.LOGGER.debug("Malformed emote message from {}", player.getName().getString(), e);
        }
    }

    public static byte[] remotePlay(UUID player, ResourceLocation animation, Options options, int elapsedTicks) {
        return encode(S2C_PLAY, buf -> {
            buf.writeUUID(player);
            buf.writeUtf(animation.toString(), MAX_ID_LENGTH);
            options.write(buf);
            buf.writeVarInt(Math.max(0, elapsedTicks));
        });
    }

    public static byte[] remoteStop(UUID player) {
        return encode(S2C_STOP, buf -> buf.writeUUID(player));
    }

    public static byte[] denied(int sequence, Denial reason) {
        return encode(S2C_DENIED, buf -> {
            buf.writeVarInt(sequence);
            buf.writeByte(reason.ordinal());
        });
    }

    public static byte[] rules(ServerRules rules) {
        return encode(S2C_CONFIG, buf -> {
            buf.writeVarInt(rules.cooldownTicks());
            buf.writeByte((rules.sync() ? ServerRules.SYNC : 0) | (rules.partner() ? ServerRules.PARTNER : 0));
        });
    }

    public static byte[] partnerPlay(PartnerPlay play) {
        return encode(S2C_PARTNER_PLAY, buf -> {
            buf.writeVarInt(play.id());
            buf.writeUUID(play.starter());
            buf.writeUUID(play.partner());
            buf.writeDouble(play.x());
            buf.writeDouble(play.y());
            buf.writeDouble(play.z());
            buf.writeFloat(play.yaw());
            buf.writeFloat(play.distance());
            buf.writeUtf(play.starterAnimation().toString(), MAX_ID_LENGTH);
            buf.writeUtf(play.partnerAnimation().toString(), MAX_ID_LENGTH);
            play.options().write(buf);
            buf.writeVarInt(Math.max(0, play.elapsedTicks()));
        });
    }

    public static byte[] partnerEnd(int id, UUID starter, UUID partner) {
        return encode(S2C_PARTNER_END, buf -> {
            buf.writeVarInt(id);
            buf.writeUUID(starter);
            buf.writeUUID(partner);
        });
    }

    public static byte[] request(UUID starter, String starterName, ResourceLocation emote, String emoteName, int seconds) {
        return encode(S2C_REQUEST, buf -> {
            buf.writeUUID(starter);
            buf.writeUtf(truncate(starterName), MAX_NAME_LENGTH * 4);
            buf.writeUtf(emote.toString(), MAX_ID_LENGTH);
            buf.writeUtf(truncate(emoteName), MAX_NAME_LENGTH * 4);
            buf.writeVarInt(seconds);
        });
    }

    public static byte[] requestCancel(UUID starter) {
        return encode(S2C_REQUEST_CANCEL, buf -> buf.writeUUID(starter));
    }

    public static byte[] status(StatusCode code, String name) {
        return encode(S2C_STATUS, buf -> {
            buf.writeByte(code.ordinal());
            buf.writeUtf(truncate(name), MAX_NAME_LENGTH * 4);
        });
    }

    public static byte[] packManifest(int generation, List<PackEntry> entries) {
        return encode(S2C_PACKS_MANIFEST, buf -> {
            buf.writeVarInt(generation);
            buf.writeVarInt(entries.size());
            for (PackEntry entry : entries) {
                buf.writeUtf(entry.path(), MAX_PATH_LENGTH);
                buf.writeBytes(entry.sha1());
                buf.writeVarInt(entry.size());
            }
        });
    }

    public static byte[] packChunk(int generation, int index, int chunk, int chunks, byte[] data, int offset, int length) {
        return encode(S2C_PACKS_DATA, buf -> {
            buf.writeVarInt(generation);
            buf.writeVarInt(index);
            buf.writeVarInt(chunk);
            buf.writeVarInt(chunks);
            buf.writeVarInt(length);
            buf.writeBytes(data, offset, length);
        });
    }

    // null if the message is malformed or from another protocol version
    @Nullable
    public static ClientMessage decodeClient(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        try {
            if (buf.readUnsignedByte() != PROTOCOL) {
                return null;
            }

            return switch (buf.readUnsignedByte()) {
                case S2C_PLAY -> {
                    UUID player = buf.readUUID();
                    ResourceLocation animation = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
                    Options options = Options.read(buf);
                    int elapsed = buf.readVarInt();
                    yield animation == null ? null : new RemotePlay(player, animation, options, elapsed);
                }
                case S2C_STOP -> new RemoteStop(buf.readUUID());
                case S2C_DENIED -> new Denied(buf.readVarInt(), Denial.byId(buf.readUnsignedByte()));
                case S2C_CONFIG -> {
                    int cooldown = buf.readVarInt();
                    int flags = buf.readUnsignedByte();
                    yield new Rules(new ServerRules(cooldown, (flags & ServerRules.SYNC) != 0, (flags & ServerRules.PARTNER) != 0));
                }
                case S2C_PARTNER_PLAY -> {
                    int id = buf.readVarInt();
                    UUID starter = buf.readUUID();
                    UUID partner = buf.readUUID();
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    float yaw = buf.readFloat();
                    float distance = buf.readFloat();
                    ResourceLocation starterAnimation = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
                    ResourceLocation partnerAnimation = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
                    Options options = Options.read(buf);
                    int elapsed = buf.readVarInt();
                    if (starterAnimation == null || partnerAnimation == null || !Double.isFinite(x + y + z + yaw + distance)) {
                        yield null;
                    }

                    yield new PartnerPlay(id, starter, partner, x, y, z, yaw, Math.max(0, Math.min(PartnerSpec.MAX_DISTANCE, distance)),
                            starterAnimation, partnerAnimation, options, elapsed);
                }
                case S2C_PARTNER_END -> new PartnerEnd(buf.readVarInt(), buf.readUUID(), buf.readUUID());
                case S2C_REQUEST -> {
                    UUID starter = buf.readUUID();
                    String starterName = truncate(buf.readUtf(MAX_NAME_LENGTH * 4));
                    ResourceLocation emote = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
                    String emoteName = truncate(buf.readUtf(MAX_NAME_LENGTH * 4));
                    int seconds = buf.readVarInt();
                    yield emote == null ? null : new Request(starter, starterName, emote, emoteName, seconds);
                }
                case S2C_REQUEST_CANCEL -> new RequestCancel(buf.readUUID());
                case S2C_STATUS -> {
                    int code = buf.readUnsignedByte();
                    String name = truncate(buf.readUtf(MAX_NAME_LENGTH * 4));
                    yield code < StatusCode.VALUES.length ? new Status(StatusCode.VALUES[code], name) : null;
                }
                case S2C_PACKS_MANIFEST -> {
                    int generation = buf.readVarInt();
                    int count = buf.readVarInt();
                    if (count < 0 || count > MAX_PACK_FILES) {
                        yield null;
                    }

                    List<PackEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String path = buf.readUtf(MAX_PATH_LENGTH);
                        byte[] sha1 = new byte[SHA1_LENGTH];
                        buf.readBytes(sha1);
                        entries.add(new PackEntry(path, sha1, buf.readVarInt()));
                    }

                    yield new PackManifest(generation, entries);
                }
                case S2C_PACKS_DATA -> {
                    int generation = buf.readVarInt();
                    int index = buf.readVarInt();
                    int chunk = buf.readVarInt();
                    int chunks = buf.readVarInt();
                    int length = buf.readVarInt();
                    if (length < 0 || length > PACK_CHUNK_SIZE || length > buf.readableBytes()) {
                        yield null;
                    }

                    byte[] bytes = new byte[length];
                    buf.readBytes(bytes);
                    yield new PackChunk(generation, index, chunk, chunks, bytes);
                }
                default -> null;
            };
        } catch (RuntimeException e) {
            PlayerEmotes.LOGGER.debug("Malformed emote message from server", e);
            return null;
        }
    }

    private static String truncate(String text) {
        return text.length() > MAX_NAME_LENGTH ? text.substring(0, MAX_NAME_LENGTH) : text;
    }

    private static byte[] encode(int type, Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(PROTOCOL);
        buf.writeByte(type);
        writer.accept(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }

    // playback settings sent along with an animation, sound is null for silent emotes, prop is the item held during the
    // emote, null for none, splitLimbs draws bent limbs as two rigid halves. a flag older versions do not know is
    // ignored by them, so new flags need no protocol change
    public record Options(boolean look, boolean splitLimbs, int blendInTicks, int blendOutTicks, @Nullable EmoteSound sound,
                          @Nullable EmoteProp prop) {

        public static final Options DEFAULT = new Options(true, false, 3, 4, null, null);
        private static final int LOOK = 1;
        private static final int SOUND = 2;
        private static final int PROP = 4;
        private static final int SPLIT_LIMBS = 8;

        public Options withoutSound() {
            return this.sound == null ? this : new Options(this.look, this.splitLimbs, this.blendInTicks, this.blendOutTicks, null, this.prop);
        }

        void write(FriendlyByteBuf buf) {
            buf.writeByte((this.look ? LOOK : 0) | (this.sound != null ? SOUND : 0) | (this.prop != null ? PROP : 0)
                    | (this.splitLimbs ? SPLIT_LIMBS : 0));
            buf.writeByte(clampByte(this.blendInTicks));
            buf.writeByte(clampByte(this.blendOutTicks));
            if (this.sound != null) {
                this.sound.write(buf);
            }

            if (this.prop != null) {
                this.prop.write(buf);
            }
        }

        static Options read(FriendlyByteBuf buf) {
            int flags = buf.readUnsignedByte();
            int blendIn = buf.readUnsignedByte();
            int blendOut = buf.readUnsignedByte();
            EmoteSound sound = (flags & SOUND) != 0 ? EmoteSound.read(buf) : null;
            EmoteProp prop = (flags & PROP) != 0 ? EmoteProp.read(buf) : null;
            return new Options((flags & LOOK) != 0, (flags & SPLIT_LIMBS) != 0, blendIn, blendOut, sound, prop);
        }

        private static int clampByte(int value) {
            return Math.max(0, Math.min(255, value));
        }
    }

    // why the server refused an emote of the local player
    public enum Denial {
        // emotes are turned off on this server
        DISABLED,
        // the player lacks the permission, or the emote is disabled or restricted
        NOT_ALLOWED,
        // too soon after the previous emote
        COOLDOWN,
        // the emote to sync with is gone or out of reach
        SYNC_FAILED;

        private static final Denial[] VALUES = values();

        static Denial byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : NOT_ALLOWED;
        }
    }

    // what the server allows, sent in reply to hello
    public record ServerRules(int cooldownTicks, boolean sync, boolean partner) {

        // assumed until the server answers (or when it does not have the mod)
        public static final ServerRules UNKNOWN = new ServerRules(0, false, false);
        private static final int SYNC = 1;
        private static final int PARTNER = 2;
    }

    // a two-player emote: what the starter plays while waiting, then what each of the two plays
    public record PartnerSpec(ResourceLocation intro, ResourceLocation action, ResourceLocation partnerAction, float distance) {

        // farthest the two may be apart in the animations, in blocks
        public static final float MAX_DISTANCE = 4;

        public PartnerSpec {
            distance = Math.max(0, Math.min(MAX_DISTANCE, distance));
        }

        void write(FriendlyByteBuf buf) {
            buf.writeUtf(this.intro.toString(), MAX_ID_LENGTH);
            buf.writeUtf(this.action.toString(), MAX_ID_LENGTH);
            buf.writeUtf(this.partnerAction.toString(), MAX_ID_LENGTH);
            buf.writeFloat(this.distance);
        }

        @Nullable
        static PartnerSpec read(FriendlyByteBuf buf) {
            ResourceLocation intro = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
            ResourceLocation action = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
            ResourceLocation partnerAction = ResourceLocation.tryParse(buf.readUtf(MAX_ID_LENGTH));
            float distance = buf.readFloat();
            if (intro == null || action == null || partnerAction == null || !Float.isFinite(distance)) {
                return null;
            }

            return new PartnerSpec(intro, action, partnerAction, distance);
        }
    }

    // what happened to the local player's partner emote
    public enum StatusCode {
        // a request went to name
        REQUEST_SENT,
        // nobody in front, anyone can join
        WAITING_OPEN,
        // name did not answer, anyone can join now
        REQUEST_EXPIRED,
        // name left, anyone can join now
        PARTNER_LEFT,
        // accepting found nobody waiting in front
        NO_PARTNER,
        // accepting name's request needs being closer and facing them
        GET_CLOSER,
        // partner emotes are off or not allowed
        NOT_ALLOWED;

        private static final StatusCode[] VALUES = values();
    }

    // one file of the server's emote packs, path is <namespace>/<path below playeremotes/>
    public record PackEntry(String path, byte[] sha1, int size) {}

    public sealed interface ClientMessage permits RemotePlay, RemoteStop, Denied, Rules, PackManifest, PackChunk,
            PartnerPlay, PartnerEnd, Request, RequestCancel, Status {}

    // a player started an emote elapsedTicks ago
    public record RemotePlay(UUID player, ResourceLocation animation, Options options, int elapsedTicks) implements ClientMessage {}

    public record RemoteStop(UUID player) implements ClientMessage {}

    // the local player's emote number sequence was refused
    public record Denied(int sequence, Denial reason) implements ClientMessage {}

    public record Rules(ServerRules rules) implements ClientMessage {}

    // two players start a partner emote together. they are drawn as if starter stood at the anchor facing yaw and
    // partner stood distance blocks in front of them, facing back
    public record PartnerPlay(int id, UUID starter, UUID partner, double x, double y, double z, float yaw, float distance,
                              ResourceLocation starterAnimation, ResourceLocation partnerAnimation, Options options,
                              int elapsedTicks) implements ClientMessage {}

    public record PartnerEnd(int id, UUID starter, UUID partner) implements ClientMessage {}

    // starter asks the local player to join their partner emote
    public record Request(UUID starter, String starterName, ResourceLocation emote, String emoteName, int seconds) implements ClientMessage {}

    public record RequestCancel(UUID starter) implements ClientMessage {}

    public record Status(StatusCode code, String name) implements ClientMessage {}

    // the server's emote pack files, a new generation replaces the previous one
    public record PackManifest(int generation, List<PackEntry> entries) implements ClientMessage {}

    // part chunk of chunks of the deflate-compressed file index
    public record PackChunk(int generation, int index, int chunk, int chunks, byte[] data) implements ClientMessage {}
}
