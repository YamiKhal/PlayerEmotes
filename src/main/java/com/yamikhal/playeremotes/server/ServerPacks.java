package com.yamikhal.playeremotes.server;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.Deflater;

// emote packs from data packs (data/<namespace>/playeremotes/, same layout as resource packs), sent to players so
// a server can have its own emotes. players get a manifest of hashes and only download what their cache lacks,
// compressed and throttled so joining stays smooth
public final class ServerPacks {

    // compressed bytes per player per tick (about 1.3 MB/s)
    private static final int BYTES_PER_TICK = 64 * 1024;
    // pending data messages per player
    private static final Map<UUID, Deque<byte[]>> QUEUES = new ConcurrentHashMap<>();

    private static volatile Snapshot snapshot = new Snapshot(0, List.of());
    private static volatile boolean changed;

    private ServerPacks() {}

    // reads the packs of the server's data packs, registered as server data reload listener
    public static void reload(ResourceManager manager) {
        Map<ResourceLocation, Resource> found = new TreeMap<>(manager.listResources(PlayerEmotes.MOD_ID,
                path -> path.getPath().endsWith(".json")));
        List<PackFile> files = new ArrayList<>();
        long total = 0;
        for (Map.Entry<ResourceLocation, Resource> file : found.entrySet()) {
            String path = file.getKey().getNamespace() + "/" + file.getKey().getPath().substring(PlayerEmotes.MOD_ID.length() + 1);
            // too long for the manifest, encoding would throw on the server thread
            if (path.length() > EmoteNetwork.MAX_PATH_LENGTH) {
                PlayerEmotes.LOGGER.warn("Server emote file {} has a path longer than {} characters and is not sent", file.getKey(),
                        EmoteNetwork.MAX_PATH_LENGTH);
                continue;
            }

            try (InputStream in = file.getValue().open()) {
                byte[] data = in.readNBytes(EmoteNetwork.MAX_PACK_FILE_SIZE + 1);
                if (data.length > EmoteNetwork.MAX_PACK_FILE_SIZE) {
                    PlayerEmotes.LOGGER.warn("Server emote file {} is larger than {} bytes and is not sent", file.getKey(), EmoteNetwork.MAX_PACK_FILE_SIZE);
                    continue;
                }

                if (total + data.length > EmoteNetwork.MAX_PACK_TOTAL_SIZE || files.size() >= EmoteNetwork.MAX_PACK_FILES) {
                    PlayerEmotes.LOGGER.warn("Server emote packs exceed the size limit; {} and later files are not sent", file.getKey());
                    break;
                }

                total += data.length;
                files.add(new PackFile(new EmoteNetwork.PackEntry(path, sha1(data), data.length), deflate(data)));
            } catch (IOException e) {
                PlayerEmotes.LOGGER.error("Failed to read server emote file {}", file.getKey(), e);
            }
        }

        snapshot = new Snapshot(snapshot.generation + 1, List.copyOf(files));
        changed = true;
        if (!files.isEmpty()) {
            PlayerEmotes.LOGGER.info("Loaded {} server emote files ({} KB)", files.size(), total / 1024);
        }
    }

    // sends the manifest to a player that just said hello, if there is anything
    public static void onHello(ServerPlayer player) {
        Snapshot current = snapshot;
        if (ServerConfig.get().sendServerPacks && !current.files.isEmpty()) {
            sendManifest(player, current);
        }
    }

    // queues the requested files of the current manifest
    public static void request(ServerPlayer player, int generation, int[] indices) {
        Snapshot current = snapshot;
        if (generation != current.generation || !ServerConfig.get().sendServerPacks) {
            return;
        }

        Deque<byte[]> queue = QUEUES.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
        synchronized (queue) {
            queue.clear();
            for (int index : indices) {
                if (index < 0 || index >= current.files.size()) continue;

                byte[] data = current.files.get(index).compressed;
                int chunks = Math.max(1, (data.length + EmoteNetwork.PACK_CHUNK_SIZE - 1) / EmoteNetwork.PACK_CHUNK_SIZE);
                for (int chunk = 0; chunk < chunks; chunk++) {
                    int offset = chunk * EmoteNetwork.PACK_CHUNK_SIZE;
                    int length = Math.min(EmoteNetwork.PACK_CHUNK_SIZE, data.length - offset);
                    queue.add(EmoteNetwork.packChunk(generation, index, chunk, chunks, data, offset, length));
                }
            }
        }
    }

    // sends queued data within the per tick budget, and new manifests after a data pack reload
    public static void tick(MinecraftServer server) {
        if (changed) {
            changed = false;
            QUEUES.clear();
            Snapshot current = snapshot;
            if (ServerConfig.get().sendServerPacks) {
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (EmoteTracker.saidHello(player)) {
                        sendManifest(player, current);
                    }
                }
            }
        }

        if (QUEUES.isEmpty()) {
            return;
        }

        QUEUES.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true;
            }

            Deque<byte[]> queue = entry.getValue();
            synchronized (queue) {
                int budget = BYTES_PER_TICK;
                while (budget > 0 && !queue.isEmpty()) {
                    byte[] message = queue.poll();
                    PlayerEmotes.platform().sendToPlayer(player, message);
                    budget -= message.length;
                }

                return queue.isEmpty();
            }
        });
    }

    public static void onDisconnect(ServerPlayer player) {
        QUEUES.remove(player.getUUID());
    }

    public static byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void sendManifest(ServerPlayer player, Snapshot current) {
        List<EmoteNetwork.PackEntry> entries = new ArrayList<>(current.files.size());
        for (PackFile file : current.files) {
            entries.add(file.entry);
        }

        PlayerEmotes.platform().sendToPlayer(player, EmoteNetwork.packManifest(current.generation, entries));
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length / 4));
            byte[] buffer = new byte[8192];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }

            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    // file prepared for sending
    private record PackFile(EmoteNetwork.PackEntry entry, byte[] compressed) {}

    private record Snapshot(int generation, List<PackFile> files) {}
}
