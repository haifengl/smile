/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamConstants;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.io.Read;

/**
 * Versioned binary container envelope ({@code SMLM}) for serialized SMILE
 * {@link Model} artifacts ({@code .sml}).
 *
 * <h2>Envelope Header Layout (11 bytes, big-endian)</h2>
 * <pre>{@code
 * Offset  Size  Field       Description
 * 0       4 B   magic       ASCII "SMLM" (0x53 0x4D 0x4C 0x4D)
 * 4       2 B   formatVer   uint16 big-endian format version (currently 1)
 * 6       1 B   flags       uint8 bitfield (must be 0x00 in v1;
 *                           bit0 reserved=compression, bit2 reserved=encryption)
 * 7       4 B   headerCrc   CRC-32 (uint32 big-endian) of the preceding 7 bytes
 * 11      N B   payload     Java ObjectOutputStream payload bytes
 * }</pre>
 *
 * <p>On read, {@link #read(Path)} sniffs the leading 4 bytes:
 * <ul>
 *   <li>If {@code "SMLM"} ({@code 0x534D4C4D}) is present, the format version,
 *       flags, and header CRC-32 are validated before handing the payload to
 *       {@link Read#object(InputStream)}.</li>
 *   <li>If the legacy Java serialization magic ({@code 0xACED0005}) is present,
 *       the stream is deserialized via the legacy path and a one-time
 *       deprecation warning is logged.</li>
 * </ul>
 *
 * @author Haifeng Li
 */
public final class Format {
    private static final Logger logger = LoggerFactory.getLogger(Format.class);

    /** 4-byte ASCII magic {@code "SMLM"} ({@code 0x534D4C4D}). */
    public static final int MAGIC = 0x534D4C4D;

    /** Current envelope format version ({@code 1}). */
    public static final short CURRENT_VERSION = 1;

    /** Minimum supported envelope format version ({@code 1}). */
    public static final short MIN_VERSION = 1;

    /** Size in bytes of the fixed {@code SMLM} envelope header ({@code 11}). */
    public static final int HEADER_SIZE = 11;

    /** Reserved flag bit 0: payload compression. */
    public static final byte FLAG_COMPRESSION = 0x01;

    /** Reserved flag bit 2: payload encryption. */
    public static final byte FLAG_ENCRYPTION = 0x04;

    /** Legacy Java ObjectOutputStream header magic + version ({@code 0xACED0005}). */
    static final int LEGACY_STREAM_HEADER =
            ((Short.toUnsignedInt(ObjectStreamConstants.STREAM_MAGIC) << 16)
                    | Short.toUnsignedInt(ObjectStreamConstants.STREAM_VERSION));

    /** Ensures the legacy-format deprecation warning is logged at most once per JVM run. */
    private static final AtomicBoolean LEGACY_WARNING_LOGGED = new AtomicBoolean(false);

    private Format() {
    }

    /**
     * Parsed {@code SMLM} envelope header metadata.
     *
     * @param version the envelope format version ({@code >= 1} for enveloped artifacts,
     *                or {@code 0} for legacy raw {@code ObjectOutputStream} artifacts).
     * @param flags   the envelope flags byte ({@code 0} in v1).
     * @param crc32   the unsigned 32-bit CRC-32 of the first 7 header bytes
     *                ({@code 0} for legacy artifacts).
     * @param legacy  {@code true} if the artifact uses the legacy raw {@code ObjectOutputStream} header.
     */
    public record Header(int version, int flags, long crc32, boolean legacy) {
    }

    /**
     * Computes the 11-byte {@code SMLM} header for the given version and flags.
     *
     * @param version the format version.
     * @param flags   the flags byte.
     * @return the 11-byte header array.
     */
    public static byte[] buildHeader(short version, byte flags) {
        byte[] header = new byte[HEADER_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(header);
        buf.putInt(MAGIC);
        buf.putShort(version);
        buf.put(flags);
        CRC32 crc = new CRC32();
        crc.update(header, 0, 7);
        buf.putInt((int) crc.getValue());
        return header;
    }

    /**
     * Reads and validates the envelope header from a model file without
     * deserializing the model payload.
     *
     * @param path the model file path.
     * @return the parsed {@link Header}.
     * @throws IOException if the file cannot be read or the header is invalid.
     */
    public static Header inspect(Path path) throws IOException {
        Objects.requireNonNull(path, "Model path must not be null");
        try (InputStream in = Files.newInputStream(path);
             PushbackInputStream pb = new PushbackInputStream(in, 4)) {
            return readHeader(pb, path.toString());
        }
    }

    /**
     * Writes a SMILE {@link Model} to a file with the {@code SMLM} v1 envelope header.
     *
     * @param model the model to serialize.
     * @param path  the destination file path.
     * @throws IOException if an I/O error occurs or the model is not {@link Serializable}.
     */
    public static void write(Model model, Path path) throws IOException {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(path, "Path must not be null");
        try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(path))) {
            write(model, file);
        }
    }

    /**
     * Writes a SMILE {@link Model} to an output stream with the {@code SMLM} v1 envelope header.
     *
     * @param model the model to serialize.
     * @param out   the destination output stream.
     * @throws IOException if an I/O error occurs or the model is not {@link Serializable}.
     */
    public static void write(Model model, OutputStream out) throws IOException {
        Objects.requireNonNull(model, "Model must not be null");
        Objects.requireNonNull(out, "OutputStream must not be null");
        if (!(model instanceof Serializable s)) {
            throw new IllegalArgumentException("Model is not Serializable: " + model.getClass().getName());
        }
        out.write(buildHeader(CURRENT_VERSION, (byte) 0));
        try (ObjectOutputStream oos = new ObjectOutputStream(out)) {
            oos.writeObject(s);
        }
    }

    /**
     * Reads a SMILE {@link Model} from a file, supporting both the {@code SMLM}
     * versioned envelope and legacy raw {@code ObjectOutputStream} artifacts.
     *
     * @param path the model file path.
     * @return the deserialized {@link Model}.
     * @throws IOException            if the file cannot be read, the header is corrupt/unsupported,
     *                                a disallowed class is encountered, or the payload is not a {@link Model}.
     * @throws ClassNotFoundException if a serialized class cannot be found.
     */
    public static Model read(Path path) throws IOException, ClassNotFoundException {
        return read(path, null);
    }

    /**
     * Reads a SMILE {@link Model} from a file with an additional {@link ObjectInputFilter}.
     *
     * @param path        the model file path.
     * @param extraFilter optional extra {@link ObjectInputFilter} merged with the built-in allow-list.
     * @return the deserialized {@link Model}.
     * @throws IOException            if the file cannot be read, the header is corrupt/unsupported,
     *                                a disallowed class is encountered, or the payload is not a {@link Model}.
     * @throws ClassNotFoundException if a serialized class cannot be found.
     */
    public static Model read(Path path, ObjectInputFilter extraFilter) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(path, "Model path must not be null");
        try (InputStream file = new BufferedInputStream(Files.newInputStream(path))) {
            return read(file, path.toString(), extraFilter);
        }
    }

    /**
     * Reads a SMILE {@link Model} from an input stream.
     *
     * @param in the input stream.
     * @return the deserialized {@link Model}.
     * @throws IOException            if the stream cannot be read, the header is corrupt/unsupported,
     *                                or the payload is not a {@link Model}.
     * @throws ClassNotFoundException if a serialized class cannot be found.
     */
    public static Model read(InputStream in) throws IOException, ClassNotFoundException {
        return read(in, "<stream>", null);
    }

    private static Model read(InputStream in, String sourceName, ObjectInputFilter extraFilter)
            throws IOException, ClassNotFoundException {
        Objects.requireNonNull(in, "InputStream must not be null");
        PushbackInputStream pb = new PushbackInputStream(in, 4);
        Header header = readHeader(pb, sourceName);
        if (header.legacy()) {
            if (LEGACY_WARNING_LOGGED.compareAndSet(false, true)) {
                logger.warn("Loading legacy unversioned .sml artifact '{}'; re-save with Model.write() to upgrade to the SMLM v{} envelope.",
                        sourceName, CURRENT_VERSION);
            }
        }
        Object obj = extraFilter == null ? Read.object(pb) : Read.object(pb, extraFilter);
        if (obj instanceof Model model) {
            return model;
        }
        throw new StreamCorruptedException(String.format(
                "'%s' does not contain a valid SMILE Model (got %s)",
                sourceName, obj == null ? "null" : obj.getClass().getName()));
    }

    private static Header readHeader(PushbackInputStream pb, String sourceName) throws IOException {
        byte[] prefix = pb.readNBytes(4);
        if (prefix.length < 4) {
            throw new EOFException(String.format(
                    "Truncated .sml artifact '%s' (expected at least 4 bytes, got %d)",
                    sourceName, prefix.length));
        }
        int magic = ByteBuffer.wrap(prefix).getInt();
        if (magic == MAGIC) {
            byte[] rest = pb.readNBytes(HEADER_SIZE - 4);
            if (rest.length < HEADER_SIZE - 4) {
                throw new EOFException(String.format(
                        "Truncated SMLM envelope header in '%s' (expected %d bytes, got %d)",
                        sourceName, HEADER_SIZE, 4 + rest.length));
            }
            ByteBuffer buf = ByteBuffer.wrap(rest);
            int formatVer = Short.toUnsignedInt(buf.getShort());
            int flags = Byte.toUnsignedInt(buf.get());
            long expectedCrc = Integer.toUnsignedLong(buf.getInt());

            CRC32 crc = new CRC32();
            crc.update(prefix);
            crc.update(rest, 0, 3);
            long actualCrc = crc.getValue();
            if (actualCrc != expectedCrc) {
                throw new StreamCorruptedException(String.format(
                        "Corrupted SMLM envelope header in '%s': CRC-32 mismatch (expected 0x%08X, got 0x%08X)",
                        sourceName, expectedCrc, actualCrc));
            }
            if (formatVer < MIN_VERSION || formatVer > CURRENT_VERSION) {
                throw new StreamCorruptedException(String.format(
                        "Unsupported .sml format: '%s' is a v%d .sml; this runtime reads v%d–v%d",
                        sourceName, formatVer, MIN_VERSION, CURRENT_VERSION));
            }
            if (flags != 0) {
                throw new StreamCorruptedException(String.format(
                        "Unsupported SMLM header flags 0x%02X in '%s' (v%d requires 0x00)",
                        flags, sourceName, formatVer));
            }
            return new Header(formatVer, flags, actualCrc, false);
        }

        if (magic == LEGACY_STREAM_HEADER) {
            pb.unread(prefix);
            return new Header(0, 0, 0L, true);
        }

        throw new StreamCorruptedException(String.format(
                "Invalid .sml header magic 0x%08X in '%s' (expected SMLM 0x%08X or legacy stream 0x%08X)",
                magic, sourceName, MAGIC, LEGACY_STREAM_HEADER));
    }

    /**
     * Resets the one-time legacy warning flag (for unit testing only).
     */
    static void resetLegacyWarningForTests() {
        LEGACY_WARNING_LOGGED.set(false);
    }

    /**
     * Returns {@code true} if the one-time legacy warning has been emitted.
     */
    static boolean isLegacyWarningLogged() {
        return LEGACY_WARNING_LOGGED.get();
    }
}
