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
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.Serializable;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.CRC32;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.io.SmileObjectInputStream;

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
 *       {@link SmileObjectInputStream}.</li>
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
    public static final int MAGIC = SmileObjectInputStream.SMLM_MAGIC;

    /** Current envelope format version ({@code 1}). */
    public static final short CURRENT_VERSION = SmileObjectInputStream.SMLM_CURRENT_VERSION;

    /** Minimum supported envelope format version ({@code 1}). */
    public static final short MIN_VERSION = SmileObjectInputStream.SMLM_MIN_VERSION;

    /** Size in bytes of the fixed {@code SMLM} envelope header ({@code 11}). */
    public static final int HEADER_SIZE = SmileObjectInputStream.SMLM_HEADER_SIZE;

    /** Reserved flag bit 0: payload compression. */
    public static final byte FLAG_COMPRESSION = 0x01;

    /** Reserved flag bit 2: payload encryption. */
    public static final byte FLAG_ENCRYPTION = 0x04;

    /** Flag bit 3: extended header block with payload size, payload CRC-32, and cleartext metadata. */
    public static final byte FLAG_EXTENDED_HEADER = SmileObjectInputStream.SMLM_FLAG_EXTENDED_HEADER;

    /** Legacy Java ObjectOutputStream header magic + version ({@code 0xACED0005}). */
    static final int LEGACY_STREAM_HEADER = SmileObjectInputStream.LEGACY_STREAM_HEADER;

    /** Ensures the legacy-format deprecation warning is logged at most once per JVM run. */
    private static final AtomicBoolean LEGACY_WARNING_LOGGED = new AtomicBoolean(false);

    private Format() {
    }

    /**
     * Parsed {@code SMLM} envelope header metadata.
     *
     * @param version      the envelope format version ({@code >= 1} for enveloped artifacts,
     *                     or {@code 0} for legacy raw {@code ObjectOutputStream} artifacts).
     * @param flags        the envelope flags byte ({@code 0x00} minimal or {@code 0x08} extended in v1).
     * @param crc32        the unsigned 32-bit CRC-32 of the first 7 header bytes
     *                     ({@code 0} for legacy artifacts).
     * @param legacy       {@code true} if the artifact uses the legacy raw {@code ObjectOutputStream} header.
     * @param payloadSize  exact byte length of the serialized payload ({@code -1} if minimal/legacy header).
     * @param payloadCrc32 unsigned 32-bit CRC-32 of the serialized payload ({@code 0} if minimal/legacy header).
     * @param metadata     unmodifiable cleartext metadata map from the extended header (empty if none).
     * @param headerBytes  total byte length of the header before the payload starts ({@code 0} if legacy).
     */
    public record Header(
            int version,
            int flags,
            long crc32,
            boolean legacy,
            long payloadSize,
            long payloadCrc32,
            Map<String, String> metadata,
            int headerBytes) {

        /** Compact constructor ensuring non-null immutable metadata. */
        public Header {
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }

        /**
         * Backwards-compatible constructor for minimal/legacy headers.
         *
         * @param version the envelope format version.
         * @param flags   the envelope flags byte.
         * @param crc32   the unsigned 32-bit CRC-32 of the first 7 header bytes.
         * @param legacy  {@code true} if the artifact uses the legacy raw {@code ObjectOutputStream} header.
         */
        public Header(int version, int flags, long crc32, boolean legacy) {
            this(version, flags, crc32, legacy, -1L, 0L, Map.of(), legacy ? 0 : HEADER_SIZE);
        }

        /**
         * Returns the cleartext {@code id} metadata value from the extended header, or {@code null}.
         * @return the model ID, or {@code null}.
         */
        public String modelId() {
            return metadata.get(Model.ID);
        }

        /**
         * Returns the cleartext {@code version} metadata value from the extended header, or {@code null}.
         * @return the model version, or {@code null}.
         */
        public String modelVersion() {
            return metadata.get(Model.VERSION);
        }

        /**
         * Returns the cleartext {@code algorithm} metadata value from the extended header, or {@code null}.
         * @return the algorithm name, or {@code null}.
         */
        public String algorithm() {
            return metadata.get("algorithm");
        }
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
     * deserializing the model payload. When the extended header flag ({@code 0x08})
     * is present, also verifies that the file size equals {@code headerBytes + payloadSize}.
     *
     * @param path the model file path.
     * @return the parsed {@link Header}.
     * @throws IOException if the file cannot be read or the header is invalid.
     */
    public static Header inspect(Path path) throws IOException {
        Objects.requireNonNull(path, "Model path must not be null");
        long fileSize = Files.size(path);
        Header header;
        try (InputStream in = Files.newInputStream(path);
             PushbackInputStream pb = new PushbackInputStream(in, 4)) {
            header = readHeader(pb, path.toString());
        }
        if (!header.legacy() && header.payloadSize() >= 0) {
            long expectedTotal = header.headerBytes() + header.payloadSize();
            if (fileSize != expectedTotal) {
                throw new StreamCorruptedException(String.format(
                        "SMLM payload size mismatch in '%s': header declares %d header + %d payload = %d bytes, but file is %d bytes",
                        path, header.headerBytes(), header.payloadSize(), expectedTotal, fileSize));
            }
        }
        return header;
    }

    /**
     * Writes a SMILE {@link Model} to a file with the {@code SMLM} v1 extended envelope header.
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
     * Writes a SMILE {@link Model} to an output stream with the {@code SMLM} v1 extended
     * envelope header (including {@code payloadSize}, {@code payloadCrc32}, and cleartext
     * {@code id}, {@code version}, and {@code algorithm} metadata).
     * Does not close the caller-supplied {@code out} stream, but flushes the output.
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
        Map<String, String> meta = new LinkedHashMap<>();
        if (model.tags() != null) {
            String id = model.getTag(Model.ID);
            if (id != null && !id.isBlank()) {
                meta.put(Model.ID, id);
            }
            String version = model.getTag(Model.VERSION);
            if (version != null && !version.isBlank()) {
                meta.put(Model.VERSION, version);
            }
        }
        if (model.algorithm() != null && !model.algorithm().isBlank()) {
            meta.put("algorithm", model.algorithm());
        }
        SmileObjectInputStream.writeSmlmExtended(s, meta, out);
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
        return read(path, SmileObjectInputStream.DEFAULT_FILTER);
    }

    /**
     * Reads a SMILE {@link Model} from a file with an additional {@link ObjectInputFilter}.
     *
     * @param path        the model file path.
     * @param extraFilter extra {@link ObjectInputFilter} merged with the built-in allow-list; must not be {@code null}.
     * @return the deserialized {@link Model}.
     * @throws IOException            if the file cannot be read, the header is corrupt/unsupported,
     *                                a disallowed class is encountered, or the payload is not a {@link Model}.
     * @throws ClassNotFoundException if a serialized class cannot be found.
     */
    public static Model read(Path path, ObjectInputFilter extraFilter) throws IOException, ClassNotFoundException {
        Objects.requireNonNull(path, "Model path must not be null");
        Objects.requireNonNull(extraFilter, "ObjectInputFilter must not be null");
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
        return read(in, "<stream>", SmileObjectInputStream.DEFAULT_FILTER);
    }

    private static Model read(InputStream in, String sourceName, ObjectInputFilter extraFilter)
            throws IOException, ClassNotFoundException {
        Objects.requireNonNull(in, "InputStream must not be null");
        Objects.requireNonNull(extraFilter, "ObjectInputFilter must not be null");
        PushbackInputStream pb = new PushbackInputStream(in, 4);
        SmileObjectInputStream.SmlmHeader rawHeader = SmileObjectInputStream.readSmlmHeader(pb, sourceName);
        if (rawHeader.legacy()) {
            if (LEGACY_WARNING_LOGGED.compareAndSet(false, true)) {
                logger.warn("Loading legacy unversioned .sml artifact '{}'; re-save with Model.write() to upgrade to the SMLM v{} envelope.",
                        sourceName, CURRENT_VERSION);
            }
        }
        InputStream payloadStream = SmileObjectInputStream.wrapPayloadStream(pb, rawHeader, sourceName);
        final Object obj;
        try (SmileObjectInputStream ois = new SmileObjectInputStream(payloadStream, extraFilter, true)) {
            obj = ois.readObject();
        }
        if (obj instanceof Model model) {
            return model;
        }
        throw new StreamCorruptedException(String.format(
                "'%s' does not contain a valid SMILE Model (got %s)",
                sourceName, obj == null ? "null" : obj.getClass().getName()));
    }

    private static Header readHeader(PushbackInputStream pb, String sourceName) throws IOException {
        SmileObjectInputStream.SmlmHeader h = SmileObjectInputStream.readSmlmHeader(pb, sourceName);
        return new Header(
                h.version(),
                h.flags(),
                h.crc32(),
                h.legacy(),
                h.payloadSize(),
                h.payloadCrc32(),
                h.metadata(),
                h.headerBytes());
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
