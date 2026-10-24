package dev.akashic.asynciconcompat.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StitchLayoutCache {

    private static final Logger LOG = LogManager.getLogger("AkashicAsyncCompat/StitchCache");

    private static final int MAGIC = 0x414B5343;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_PAYLOAD_BYTES = 32 * 1024 * 1024;
    private static final int MAX_STRING_BYTES = 1024 * 1024;
    private static final int MAX_ENTRIES = 100000;
    private static final int MAX_CACHE_FILES = 32;

    private StitchLayoutCache() {}

    public static final class Entry {
        public final String name;
        public final int x;
        public final int y;
        public final int packedWidth;
        public final int packedHeight;
        public final boolean rotated;

        Entry(String name, int x, int y, int packedWidth, int packedHeight, boolean rotated) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.packedWidth = packedWidth;
            this.packedHeight = packedHeight;
            this.rotated = rotated;
        }
    }

    public static final class Layout {
        public final String fingerprint;
        public final int atlasWidth;
        public final int atlasHeight;
        public final List<Entry> entries;

        Layout(String fingerprint, int atlasWidth, int atlasHeight, List<Entry> entries) {
            this.fingerprint = fingerprint;
            this.atlasWidth = atlasWidth;
            this.atlasHeight = atlasHeight;
            this.entries = Collections.unmodifiableList(entries);
        }
    }

    public static String fingerprint(
        Collection<Stitcher.Holder> holders,
        int mipmapLevel,
        int maxWidth,
        int maxHeight,
        boolean forcePowerOfTwo,
        int maxTileDimension) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateInt(digest, FORMAT_VERSION);
            updateInt(digest, mipmapLevel);
            updateInt(digest, maxWidth);
            updateInt(digest, maxHeight);
            updateBoolean(digest, forcePowerOfTwo);
            updateInt(digest, maxTileDimension);
            updateInt(digest, holders.size());

            List<Stitcher.Holder> sorted = new ArrayList<>(holders);
            Collections.sort(sorted, Comparator.comparing(holder -> holder.getAtlasSprite().getIconName()));

            String previousName = null;
            for (Stitcher.Holder holder : sorted) {
                TextureAtlasSprite sprite = holder.getAtlasSprite();
                String name = sprite.getIconName();
                if (name == null) {
                    throw new IllegalStateException("Cannot cache unnamed texture atlas sprite");
                }
                if (name.equals(previousName)) {
                    throw new IllegalStateException("Cannot cache duplicate sprite name: " + name);
                }
                previousName = name;

                updateString(digest, name);
                updateInt(digest, sprite.getIconWidth());
                updateInt(digest, sprite.getIconHeight());
                updateInt(digest, holder.getWidth());
                updateInt(digest, holder.getHeight());
                updateBoolean(digest, holder.isRotated());
            }

            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static Layout load(
        File gameDirectory,
        String fingerprint,
        Collection<Stitcher.Holder> holders,
        int maxWidth,
        int maxHeight) {

        if (!isEnabled()) return null;

        File file = cacheFile(gameDirectory, fingerprint);
        if (!file.isFile()) return null;

        try {
            byte[] raw;
            try (DataInputStream input = new DataInputStream(new FileInputStream(file))) {
                int payloadLength = input.readInt();
                if (payloadLength <= 0 || payloadLength > MAX_PAYLOAD_BYTES) {
                    throw new IOException("Invalid stitch cache payload length: " + payloadLength);
                }

                raw = new byte[payloadLength];
                input.readFully(raw);
                long expectedCrc = input.readLong();

                if (input.read() != -1) {
                    throw new IOException("Trailing data in stitch cache");
                }

                CRC32 crc = new CRC32();
                crc.update(raw);
                if (crc.getValue() != expectedCrc) {
                    throw new IOException("CRC mismatch in stitch cache");
                }
            }

            Layout layout;
            try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(raw))) {
                int magic = input.readInt();
                int version = input.readInt();
                if (magic != MAGIC || version != FORMAT_VERSION) {
                    throw new IOException(
                        "Unsupported stitch cache header magic=" + magic + " version=" + version);
                }

                String storedFingerprint = readString(input);
                if (!fingerprint.equals(storedFingerprint)) {
                    throw new IOException("Stitch cache fingerprint mismatch");
                }

                int atlasWidth = input.readInt();
                int atlasHeight = input.readInt();
                int count = input.readInt();
                if (atlasWidth <= 0 || atlasHeight <= 0
                    || atlasWidth > maxWidth || atlasHeight > maxHeight) {
                    throw new IOException(
                        "Invalid cached atlas bounds " + atlasWidth + "x" + atlasHeight);
                }
                if (count < 0 || count > MAX_ENTRIES || count != holders.size()) {
                    throw new IOException(
                        "Invalid cached entry count " + count + " holders=" + holders.size());
                }

                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    String name = readString(input);
                    int x = input.readInt();
                    int y = input.readInt();
                    int width = input.readInt();
                    int height = input.readInt();
                    boolean rotated = input.readBoolean();
                    entries.add(new Entry(name, x, y, width, height, rotated));
                }

                if (input.read() != -1) {
                    throw new IOException("Trailing payload bytes in stitch cache");
                }

                layout = new Layout(fingerprint, atlasWidth, atlasHeight, entries);
            }

            validateLayout(layout, holders);
            return layout;
        } catch (IOException | RuntimeException failure) {
            LOG.warn(
                "Ignoring invalid stitch layout cache {}: {}",
                file.getName(),
                failure.toString());
            if (!file.delete()) {
                LOG.debug("Could not delete rejected stitch cache {}", file);
            }
            return null;
        }
    }

    public static boolean save(
        File gameDirectory,
        String fingerprint,
        int atlasWidth,
        int atlasHeight,
        List<Stitcher.Slot> rootSlots,
        Collection<Stitcher.Holder> holders) {

        if (!isEnabled()) return false;
        if (atlasWidth <= 0 || atlasHeight <= 0) return false;

        try {
            List<Stitcher.Slot> leaves = new ArrayList<>();
            for (Stitcher.Slot root : rootSlots) {
                root.getAllStitchSlots(leaves);
            }

            if (leaves.size() != holders.size()) {
                LOG.warn(
                    "Not caching stitch layout: leaf count {} != holder count {}",
                    leaves.size(),
                    holders.size());
                return false;
            }

            List<Entry> entries = new ArrayList<>(leaves.size());
            Set<String> seen = new HashSet<>();
            for (Stitcher.Slot slot : leaves) {
                Stitcher.Holder holder = slot.getStitchHolder();
                if (holder == null) {
                    LOG.warn("Not caching stitch layout: leaf without holder");
                    return false;
                }

                TextureAtlasSprite sprite = holder.getAtlasSprite();
                String name = sprite.getIconName();
                if (name == null || !seen.add(name)) {
                    LOG.warn("Not caching stitch layout: invalid or duplicate sprite {}", name);
                    return false;
                }

                entries.add(new Entry(
                    name,
                    slot.getOriginX(),
                    slot.getOriginY(),
                    holder.getWidth(),
                    holder.getHeight(),
                    holder.isRotated()));
            }

            Layout layout = new Layout(fingerprint, atlasWidth, atlasHeight, entries);
            validateLayout(layout, holders);

            byte[] payload;
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                 DataOutputStream output = new DataOutputStream(bytes)) {

                output.writeInt(MAGIC);
                output.writeInt(FORMAT_VERSION);
                writeString(output, fingerprint);
                output.writeInt(atlasWidth);
                output.writeInt(atlasHeight);
                output.writeInt(entries.size());

                for (Entry entry : entries) {
                    writeString(output, entry.name);
                    output.writeInt(entry.x);
                    output.writeInt(entry.y);
                    output.writeInt(entry.packedWidth);
                    output.writeInt(entry.packedHeight);
                    output.writeBoolean(entry.rotated);
                }

                output.flush();
                payload = bytes.toByteArray();
            }

            if (payload.length > MAX_PAYLOAD_BYTES) {
                LOG.warn("Not caching stitch layout: payload too large {} bytes", payload.length);
                return false;
            }

            CRC32 crc = new CRC32();
            crc.update(payload);

            File file = cacheFile(gameDirectory, fingerprint);
            File directory = file.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("Cannot create stitch cache directory " + directory);
            }

            File temporary = new File(directory, file.getName() + ".tmp");
            try (DataOutputStream output = new DataOutputStream(new FileOutputStream(temporary))) {
                output.writeInt(payload.length);
                output.write(payload);
                output.writeLong(crc.getValue());
                output.flush();
            }

            if (file.exists() && !file.delete()) {
                throw new IOException("Cannot replace stitch cache " + file);
            }
            if (!temporary.renameTo(file)) {
                throw new IOException("Cannot publish stitch cache " + temporary);
            }

            prune(directory, file);
            return true;
        } catch (IOException | RuntimeException failure) {
            LOG.warn("Failed writing stitch layout cache: {}", failure.toString());
            return false;
        }
    }

    public static Map<String, Stitcher.Holder> validateLayout(
        Layout layout,
        Collection<Stitcher.Holder> holders) {

        Map<String, Stitcher.Holder> byName = new HashMap<>(holders.size() * 2);
        for (Stitcher.Holder holder : holders) {
            String name = holder.getAtlasSprite().getIconName();
            if (name == null || byName.put(name, holder) != null) {
                throw new IllegalStateException("Duplicate or unnamed current sprite: " + name);
            }
        }

        if (layout.entries.size() != byName.size()) {
            throw new IllegalStateException(
                "Layout/current sprite count mismatch: "
                    + layout.entries.size() + "/" + byName.size());
        }

        Set<String> seen = new HashSet<>();
        for (Entry entry : layout.entries) {
            if (!seen.add(entry.name)) {
                throw new IllegalStateException("Duplicate cached sprite: " + entry.name);
            }

            Stitcher.Holder holder = byName.get(entry.name);
            if (holder == null) {
                throw new IllegalStateException("Cached sprite missing from current atlas: " + entry.name);
            }

            int width = holder.getWidth();
            int height = holder.getHeight();
            if (holder.isRotated() != entry.rotated) {
                int swap = width;
                width = height;
                height = swap;
            }

            if (width != entry.packedWidth || height != entry.packedHeight) {
                throw new IllegalStateException(
                    "Cached size mismatch for " + entry.name
                        + " expected=" + width + "x" + height
                        + " cached=" + entry.packedWidth + "x" + entry.packedHeight);
            }

            if (entry.x < 0 || entry.y < 0
                || entry.packedWidth <= 0 || entry.packedHeight <= 0
                || entry.x + entry.packedWidth > layout.atlasWidth
                || entry.y + entry.packedHeight > layout.atlasHeight) {
                throw new IllegalStateException(
                    "Cached bounds invalid for " + entry.name);
            }
        }

        return byName;
    }

    static File cacheFile(File gameDirectory, String fingerprint) {
        File root = new File(gameDirectory, "cache/akashic-thepatcher/stitch-layout-v1");
        return new File(root, fingerprint + ".bin");
    }

    private static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("akashic.stitchLayoutCache", "true"));
    }

    private static void prune(File directory, File keep) {
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".bin"));
        if (files == null || files.length <= MAX_CACHE_FILES) return;

        List<File> sorted = new ArrayList<>();
        Collections.addAll(sorted, files);
        Collections.sort(sorted, Comparator.comparingLong(File::lastModified));

        int remove = sorted.size() - MAX_CACHE_FILES;
        for (File file : sorted) {
            if (remove <= 0) break;
            if (file.equals(keep)) continue;
            if (file.delete()) remove--;
        }
    }

    private static void updateString(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateInt(digest, bytes.length);
        digest.update(bytes);
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }

    private static void updateBoolean(MessageDigest digest, boolean value) {
        digest.update((byte) (value ? 1 : 0));
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_STRING_BYTES) {
            throw new IOException("String too large for stitch cache");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String readString(DataInputStream input) throws IOException {
        int length;
        try {
            length = input.readInt();
        } catch (EOFException eof) {
            throw new IOException("Unexpected EOF reading stitch cache string length", eof);
        }
        if (length < 0 || length > MAX_STRING_BYTES) {
            throw new IOException("Invalid stitch cache string length: " + length);
        }

        byte[] bytes = new byte[length];
        input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(Character.forDigit((value >>> 4) & 0xf, 16));
            out.append(Character.forDigit(value & 0xf, 16));
        }
        return out.toString();
    }
}