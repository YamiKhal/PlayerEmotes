package com.yamikhal.playeremotes.client.emote;

import com.yamikhal.playeremotes.PlayerEmotes;
import com.yamikhal.playeremotes.network.EmoteNetwork;
import com.yamikhal.playeremotes.server.ServerPacks;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

// downloads server emote packs (see ServerPacks) into a cache shared by all servers, keyed by content hash, so
// each file only downloads once, client thread only
public final class ServerPackClient {

    // cache size on disk, least recently used files go first
    private static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;
    private static final Pattern VALID_PATH = Pattern.compile("[a-z0-9_.-]+(/[a-z0-9_.-]+)+\\.json");
    // downloads in progress: file index to chunks received so far
    private static final Map<Integer, byte[][]> PENDING = new HashMap<>();

    private static int generation = -1;
    private static List<EmoteNetwork.PackEntry> entries = List.of();
    private static int missing;
    // server files merged into the emote registry right now, by path
    private static Map<String, byte[]> active = Map.of();
    // manifest these came from, to record it again (see FlashbackCompat)
    @Nullable
    private static EmoteNetwork.PackManifest manifest;

    private ServerPackClient() {}

    // new manifest: use cached files, ask for the rest. a replay cannot ask, it uses the cache and whatever
    // downloads the recording holds
    public static void onManifest(EmoteNetwork.PackManifest manifest, Consumer<byte[]> send, boolean canRequest) {
        ServerPackClient.manifest = manifest;
        generation = manifest.generation();
        PENDING.clear();
        List<EmoteNetwork.PackEntry> valid = new ArrayList<>();
        long total = 0;
        for (EmoteNetwork.PackEntry entry : manifest.entries()) {
            total += entry.size();
            if (!VALID_PATH.matcher(entry.path()).matches() || entry.size() < 0 || entry.size() > EmoteNetwork.MAX_PACK_FILE_SIZE
                    || total > EmoteNetwork.MAX_PACK_TOTAL_SIZE) {
                PlayerEmotes.LOGGER.warn("Ignoring invalid server emote file {}", entry.path());
                valid.add(null);
            } else {
                valid.add(entry);
            }
        }

        entries = valid;

        List<Integer> wanted = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            EmoteNetwork.PackEntry entry = entries.get(index);
            if (entry != null && readCached(entry) == null) {
                wanted.add(index);
            }
        }

        missing = wanted.size();
        if (missing == 0 || !canRequest) {
            apply();
        }

        if (missing > 0 && canRequest) {
            PlayerEmotes.LOGGER.info("Downloading {} of {} server emote files", missing, entries.size());
            send.accept(EmoteNetwork.packsRequest(generation, wanted.stream().mapToInt(Integer::intValue).toArray()));
        }
    }

    public static void onChunk(EmoteNetwork.PackChunk chunk) {
        if (chunk.generation() != generation || chunk.index() < 0 || chunk.index() >= entries.size()) {
            return;
        }

        EmoteNetwork.PackEntry entry = entries.get(chunk.index());
        // compressed data is only a little larger than the file itself
        int maxChunks = entry == null ? 0 : entry.size() / EmoteNetwork.PACK_CHUNK_SIZE + 2;
        if (entry == null || chunk.chunks() <= 0 || chunk.chunks() > maxChunks || chunk.chunk() < 0 || chunk.chunk() >= chunk.chunks()) {
            return;
        }

        byte[][] parts = PENDING.computeIfAbsent(chunk.index(), index -> new byte[chunk.chunks()][]);
        if (parts.length != chunk.chunks() || parts[chunk.chunk()] != null) {
            return;
        }

        parts[chunk.chunk()] = chunk.data();
        for (byte[] part : parts) {
            if (part == null) {
                return;
            }
        }

        PENDING.remove(chunk.index());

        byte[] data = inflate(parts, entry.size());
        if (data == null || !Arrays.equals(ServerPacks.sha1(data), entry.sha1())) {
            PlayerEmotes.LOGGER.warn("Server emote file {} arrived damaged and is skipped", entry.path());
        } else {
            writeCached(entry, data);
        }

        if (--missing == 0) {
            apply();
        }
    }

    // server's current manifest, null if it sent none
    @Nullable
    public static EmoteNetwork.PackManifest manifest() {
        return manifest;
    }

    // leaving the server, its emotes go away
    public static void clear() {
        boolean had = !active.isEmpty();
        manifest = null;
        generation = -1;
        entries = List.of();
        PENDING.clear();
        missing = 0;
        active = Map.of();
        if (had) {
            EmoteRegistry.reload(Minecraft.getInstance().getResourceManager());
        }
    }

    // server files by <namespace>/<path>, for EmoteRegistry
    static Map<String, byte[]> files() {
        return active;
    }

    private static void apply() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (EmoteNetwork.PackEntry entry : entries) {
            if (entry == null) continue;

            byte[] data = readCached(entry);
            if (data != null) {
                files.put(entry.path(), data);
            }
        }

        if (files.isEmpty() && active.isEmpty()) {
            return;
        }

        active = Map.copyOf(files);
        EmoteRegistry.reload(Minecraft.getInstance().getResourceManager());
        trimCache();
    }

    @Nullable
    private static byte[] inflate(byte[][] parts, int size) {
        Inflater inflater = new Inflater();
        try {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            for (byte[] part : parts) {
                compressed.write(part);
            }

            inflater.setInput(compressed.toByteArray());
            byte[] out = new byte[size];
            int read = 0;
            // never inflates past the announced size, a hostile stream cannot blow up memory
            while (read < size && !inflater.finished()) {
                int n = inflater.inflate(out, read, size - read);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break;
                read += n;
            }

            if (read != size) {
                return null;
            }

            // anything beyond the announced size means the manifest was wrong
            return inflater.finished() || inflater.inflate(new byte[1]) == 0 ? out : null;
        } catch (IOException | DataFormatException e) {
            return null;
        } finally {
            inflater.end();
        }
    }

    private static Path cacheDir() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(PlayerEmotes.MOD_ID).resolve("server-packs");
    }

    private static Path cacheFile(EmoteNetwork.PackEntry entry) {
        return cacheDir().resolve(HexFormat.of().formatHex(entry.sha1()) + ".json");
    }

    @Nullable
    private static byte[] readCached(EmoteNetwork.PackEntry entry) {
        Path file = cacheFile(entry);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) != entry.size()) {
                return null;
            }

            byte[] data = Files.readAllBytes(file);
            if (!Arrays.equals(ServerPacks.sha1(data), entry.sha1())) {
                return null;
            }

            // marks it recently used for cache trimming
            Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()));
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeCached(EmoteNetwork.PackEntry entry, byte[] data) {
        try {
            Files.createDirectories(cacheDir());
            Path temp = cacheDir().resolve(HexFormat.of().formatHex(entry.sha1()) + ".tmp");
            Files.write(temp, data);
            Files.move(temp, cacheFile(entry), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            PlayerEmotes.LOGGER.warn("Could not cache server emote file {}", entry.path(), e);
        }
    }

    // deletes the least recently used files beyond MAX_CACHE_BYTES
    private static void trimCache() {
        Path dir = cacheDir();
        if (!Files.isDirectory(dir)) {
            return;
        }

        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = new ArrayList<>(stream.filter(Files::isRegularFile).toList());
            files.sort(Comparator.comparingLong(ServerPackClient::lastModified).reversed());
            long total = 0;
            for (Path file : files) {
                total += Files.size(file);
                if (total > MAX_CACHE_BYTES) {
                    Files.deleteIfExists(file);
                }
            }
        } catch (IOException e) {
            PlayerEmotes.LOGGER.debug("Could not trim the server emote cache", e);
        }
    }

    private static long lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }
}
