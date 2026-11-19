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
package smile.io;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamConstants;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

/**
 * A hardened {@link ObjectInputStream} that enforces an {@link ObjectInputFilter}
 * before any deserialization callbacks execute.
 *
 * <p>By default, the filter accepts only classes whose binary names begin with
 * the {@code smile.*} package hierarchy plus a carefully curated set of
 * standard JDK types (primitives, arrays, {@code java.lang}, {@code java.util},
 * {@code java.math}, {@code java.time}).  Everything else is rejected with
 * {@link InvalidClassException} before the object graph is materialized,
 * preventing gadget-chain exploits.</p>
 *
 * <p>Callers that genuinely need to deserialize additional trusted classes
 * should supply a custom {@link ObjectInputFilter} via the two-argument
 * constructor. That filter is composed with the built-in allow-list using
 * {@link ObjectInputFilter#merge(ObjectInputFilter, ObjectInputFilter)}, so
 * the caller's filter is consulted first and the built-in deny-all fallback
 * still applies for anything the caller does not explicitly permit.</p>
 *
 * @author Haifeng Li
 */
public class SmileObjectInputStream extends ObjectInputStream {

    /**
     * Maximum object-graph nesting depth permitted during deserialization
     * (configurable via {@code -Dsmile.io.serial.maxdepth}, default {@code 1024}).
     */
    public static final int DEFAULT_MAX_DEPTH =
            Integer.getInteger("smile.io.serial.maxdepth", 1024);

    /**
     * Maximum number of internal object references permitted during deserialization
     * (configurable via {@code -Dsmile.io.serial.maxrefs}, default {@code 50_000_000}).
     */
    public static final long DEFAULT_MAX_REFS =
            Long.getLong("smile.io.serial.maxrefs", 50_000_000L);

    /**
     * Maximum single-array length permitted during deserialization
     * (configurable via {@code -Dsmile.io.serial.maxarray}, default {@code 100_000_000}).
     */
    public static final int DEFAULT_MAX_ARRAY =
            Integer.getInteger("smile.io.serial.maxarray", 100_000_000);

    /**
     * Maximum stream byte count permitted during deserialization
     * (configurable via {@code -Dsmile.io.serial.maxbytes}, default {@code 1_073_741_824L} = 1 GiB).
     */
    public static final long DEFAULT_MAX_BYTES =
            Long.getLong("smile.io.serial.maxbytes", 1_073_741_824L);

    /**
     * The default allow-list and resource-limit pattern accepted by every instance.
     *
     * <ul>
     *   <li>{@code maxdepth}, {@code maxrefs}, {@code maxarray}, {@code maxbytes} – resource exhaustion bounds</li>
     *   <li>{@code smile.**}            – all SMILE classes</li>
     *   <li>{@code java.lang.**}        – String, Number, Enum, etc.</li>
     *   <li>{@code java.util.**}        – Collection, Map, Optional, …</li>
     *   <li>{@code java.math.**}        – BigInteger, BigDecimal</li>
     *   <li>{@code java.time.**}        – LocalDate, Instant, …</li>
     *   <li>{@code scala.**}            – Scala runtime/stdlib types for {@code smile-scala}</li>
     *   <li>{@code [*}                  – any array type</li>
     *   <li>{@code !*}                  – reject everything else</li>
     * </ul>
     */
    public static final ObjectInputFilter DEFAULT_FILTER =
            ObjectInputFilter.Config.createFilter(
                    "maxdepth=" + DEFAULT_MAX_DEPTH +
                    ";maxrefs=" + DEFAULT_MAX_REFS +
                    ";maxarray=" + DEFAULT_MAX_ARRAY +
                    ";maxbytes=" + DEFAULT_MAX_BYTES +
                    ";smile.**" +
                    ";java.lang.**" +
                    ";java.util.**" +
                    ";java.math.**" +
                    ";java.time.**" +
                    ";scala.**" +
                    ";[*" +
                    ";!*");

    /** ASCII magic {@code "SMLM"} ({@code 0x534D4C4D}) for enveloped SMILE model files. */
    public static final int SMLM_MAGIC = 0x534D4C4D;
    /** Minimum supported {@code SMLM} envelope format version. */
    public static final short SMLM_MIN_VERSION = 1;
    /** Current and maximum supported {@code SMLM} envelope format version. */
    public static final short SMLM_CURRENT_VERSION = 1;
    /** Fixed byte length of the {@code SMLM} envelope prefix header. */
    public static final int SMLM_HEADER_SIZE = 11;
    /** Flag bit 3 ({@code 0x08}): extended header block with payload size, payload CRC-32, and cleartext metadata. */
    public static final byte SMLM_FLAG_EXTENDED_HEADER = 0x08;
    /** Maximum byte length of the UTF-8 metadata block in the extended header (16 KiB). */
    public static final int SMLM_MAX_METADATA_BYTES = 16_384;
    /** Legacy Java {@link ObjectOutputStream} stream magic + version ({@code 0xACED0005}). */
    public static final int LEGACY_STREAM_HEADER =
            ((Short.toUnsignedInt(ObjectStreamConstants.STREAM_MAGIC) << 16)
                    | Short.toUnsignedInt(ObjectStreamConstants.STREAM_VERSION));

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
    public record SmlmHeader(
            int version,
            int flags,
            long crc32,
            boolean legacy,
            long payloadSize,
            long payloadCrc32,
            Map<String, String> metadata,
            int headerBytes) {

        /** Compact constructor ensuring non-null immutable metadata. */
        public SmlmHeader {
            metadata = metadata == null || metadata.isEmpty()
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        }

        /**
         * Backwards-compatible constructor for minimal/legacy headers.
         *
         * @param version the envelope format version.
         * @param flags   the envelope flags byte.
         * @param crc32   the unsigned 32-bit CRC-32 of the first 7 header bytes.
         * @param legacy  {@code true} if the artifact uses the legacy raw {@code ObjectOutputStream} header.
         */
        public SmlmHeader(int version, int flags, long crc32, boolean legacy) {
            this(version, flags, crc32, legacy, -1L, 0L, Map.of(), legacy ? 0 : SMLM_HEADER_SIZE);
        }
    }

    /**
     * Creates a {@code SmileObjectInputStream} with the default allow-list filter.
     *
     * @param in the underlying input stream.
     * @throws IOException if an I/O error occurs while reading the stream header.
     */
    public SmileObjectInputStream(InputStream in) throws IOException {
        this(in, DEFAULT_FILTER);
    }

    /**
     * Creates a {@code SmileObjectInputStream} with a caller-supplied filter
     * merged on top of the built-in allow-list.
     *
     * <p>The {@code extraFilter} is consulted first.  If it returns
     * {@link ObjectInputFilter.Status#ALLOWED} the class is accepted
     * immediately without consulting the built-in allow-list, enabling callers
     * to extend the trusted set.  If {@code extraFilter} returns
     * {@link ObjectInputFilter.Status#REJECTED} the class is blocked
     * immediately.  If it returns {@link ObjectInputFilter.Status#UNDECIDED}
     * the decision falls through to the built-in allow-list.</p>
     *
     * @param in          the underlying input stream.
     * @param extraFilter an additional {@link ObjectInputFilter} to merge; must not be {@code null}.
     * @throws IOException if an I/O error occurs while reading the stream header.
     */
    public SmileObjectInputStream(InputStream in, ObjectInputFilter extraFilter) throws IOException {
        this(stripSmlmHeaderIfPresent(in), extraFilter, true);
    }

    private final PayloadVerifyingInputStream payloadVerifier;

    /**
     * Creates a {@code SmileObjectInputStream} on a stream whose {@code SMLM}
     * envelope header has already been validated and consumed.
     *
     * @param payloadStream stream positioned directly at the {@code ObjectOutputStream} header.
     * @param extraFilter   the {@link ObjectInputFilter} to merge with {@link #DEFAULT_FILTER}.
     * @param preStripped   marker distinguishing the pre-stripped constructor.
     * @throws IOException if an I/O error occurs while reading the stream header.
     */
    public SmileObjectInputStream(InputStream payloadStream, ObjectInputFilter extraFilter, boolean preStripped)
            throws IOException {
        this(preStripped ? payloadStream : stripSmlmHeaderIfPresent(payloadStream), extraFilter, (Void) null);
    }

    private SmileObjectInputStream(InputStream preparedStream, ObjectInputFilter extraFilter, Void internal)
            throws IOException {
        super(preparedStream);
        this.payloadVerifier = preparedStream instanceof PayloadVerifyingInputStream pv ? pv : null;
        Objects.requireNonNull(extraFilter, "ObjectInputFilter must not be null");
        ObjectInputFilter combined = extraFilter == DEFAULT_FILTER
                ? DEFAULT_FILTER
                : filterInfo -> {
                    ObjectInputFilter.Status status = extraFilter.checkInput(filterInfo);
                    if (status != ObjectInputFilter.Status.UNDECIDED) {
                        return status;
                    }
                    return DEFAULT_FILTER.checkInput(filterInfo);
                };
        setObjectInputFilter(combined);
    }

    /**
     * Reads and validates the leading header of a {@code .sml} stream.
     *
     * <p>If the stream begins with the 11-byte {@code SMLM} envelope header,
     * validates the format version, flags, and CRC-32 and leaves {@code pb}
     * positioned at the Java serialization payload. If the stream begins with
     * the legacy {@code 0xACED0005} stream header, pushes the 4 bytes back into
     * {@code pb} and returns a header with {@code legacy == true}. Otherwise
     * throws {@link StreamCorruptedException}.</p>
     *
     * @param pb         a {@link PushbackInputStream} with at least a 4-byte buffer.
     * @param sourceName human-readable stream or file name for error messages.
     * @return the parsed {@link SmlmHeader}.
     * @throws IOException if the stream is truncated or the header is invalid.
     */
    public static SmlmHeader readSmlmHeader(PushbackInputStream pb, String sourceName) throws IOException {
        byte[] prefix = pb.readNBytes(4);
        if (prefix.length < 4) {
            throw new EOFException(String.format(
                    "Truncated .sml artifact '%s' (expected at least 4 bytes, got %d)",
                    sourceName, prefix.length));
        }
        int magic = ByteBuffer.wrap(prefix).getInt();
        if (magic == SMLM_MAGIC) {
            return parseSmlmHeaderRest(pb, prefix, sourceName);
        }
        if (magic == LEGACY_STREAM_HEADER) {
            pb.unread(prefix);
            return new SmlmHeader(0, 0, 0L, true);
        }
        throw new StreamCorruptedException(String.format(
                "Invalid .sml header magic 0x%08X in '%s' (expected SMLM 0x%08X or legacy stream 0x%08X)",
                magic, sourceName, SMLM_MAGIC, LEGACY_STREAM_HEADER));
    }

    /**
     * Sniffs the first 4 bytes of {@code in}. If the stream starts with the
     * {@code SMLM} model envelope header, validates the format version,
     * flags, header CRC-32, and (when {@link #SMLM_FLAG_EXTENDED_HEADER} is set)
     * the extended header CRC-32 before returning a stream positioned at the Java
     * serialization payload (wrapped in a payload length/CRC-32 verifier when
     * extended framing is present); otherwise pushes the bytes back so raw
     * {@link ObjectInputStream} streams load unchanged.
     */
    static InputStream stripSmlmHeaderIfPresent(InputStream in) throws IOException {
        PushbackInputStream pb = new PushbackInputStream(in, 4);
        byte[] magic = pb.readNBytes(4);
        if (magic.length < 4) {
            pb.unread(magic, 0, magic.length);
            return pb;
        }
        int magicInt = ByteBuffer.wrap(magic).getInt();
        if (magicInt != SMLM_MAGIC) {
            pb.unread(magic);
            return pb;
        }
        SmlmHeader header = parseSmlmHeaderRest(pb, magic, "<stream>");
        return wrapPayloadStream(pb, header, "<stream>");
    }

    /**
     * Wraps {@code payloadStream} in a size + CRC-32 verifying stream when
     * {@code header} carries {@link #SMLM_FLAG_EXTENDED_HEADER}; otherwise
     * returns {@code payloadStream} as-is.
     *
     * @param payloadStream stream positioned immediately after the {@code SMLM} header.
     * @param header        parsed {@link SmlmHeader}.
     * @param sourceName    human-readable stream or file name for error messages.
     * @return the wrapped (or original) payload stream.
     */
    public static InputStream wrapPayloadStream(InputStream payloadStream, SmlmHeader header, String sourceName) {
        if (!header.legacy() && (header.flags() & SMLM_FLAG_EXTENDED_HEADER) != 0 && header.payloadSize() >= 0) {
            return new PayloadVerifyingInputStream(payloadStream, header.payloadSize(), header.payloadCrc32(), sourceName);
        }
        return payloadStream;
    }

    /**
     * Serializes {@code object} to {@code out} wrapped in an {@code SMLM} v1
     * extended envelope header containing the exact payload byte length,
     * payload CRC-32, and cleartext {@code key=value} metadata.
     *
     * @param object   the serializable object to write.
     * @param metadata optional cleartext metadata key/value pairs (e.g. {@code id}, {@code version}, {@code algorithm}).
     * @param out      the destination output stream (flushed, not closed).
     * @throws IOException if serialization or writing fails.
     */
    public static void writeSmlmExtended(Serializable object, Map<String, String> metadata, OutputStream out)
            throws IOException {
        Objects.requireNonNull(object, "Serializable object must not be null");
        Objects.requireNonNull(out, "OutputStream must not be null");

        ByteArrayOutputStream payloadBuf = new ByteArrayOutputStream(8192);
        try (ObjectOutputStream oos = new ObjectOutputStream(payloadBuf)) {
            oos.writeObject(object);
        }
        byte[] payload = payloadBuf.toByteArray();

        CRC32 payloadCrc = new CRC32();
        payloadCrc.update(payload);
        int payloadCrc32 = (int) payloadCrc.getValue();

        byte[] metaBytes = encodeMetadata(metadata);
        if (metaBytes.length > SMLM_MAX_METADATA_BYTES) {
            throw new IOException(String.format(
                    "SMLM metadata block exceeds maximum size (%d > %d bytes)",
                    metaBytes.length, SMLM_MAX_METADATA_BYTES));
        }

        // 1. Fixed 11-byte prefix
        byte[] prefix = new byte[SMLM_HEADER_SIZE];
        ByteBuffer prefixBuf = ByteBuffer.wrap(prefix);
        prefixBuf.putInt(SMLM_MAGIC);
        prefixBuf.putShort(SMLM_CURRENT_VERSION);
        prefixBuf.put(SMLM_FLAG_EXTENDED_HEADER);
        CRC32 headerCrc = new CRC32();
        headerCrc.update(prefix, 0, 7);
        prefixBuf.putInt((int) headerCrc.getValue());

        // 2. Extended header block: payloadSize (8B) + payloadCrc32 (4B) + metaLen (2B) + metaBytes + extHeaderCrc32 (4B)
        byte[] extBody = new byte[14 + metaBytes.length];
        ByteBuffer extBuf = ByteBuffer.wrap(extBody);
        extBuf.putLong(payload.length);
        extBuf.putInt(payloadCrc32);
        extBuf.putShort((short) metaBytes.length);
        extBuf.put(metaBytes);

        CRC32 extCrc = new CRC32();
        extCrc.update(extBody);
        int extCrc32 = (int) extCrc.getValue();

        out.write(prefix);
        out.write(extBody);
        out.write(ByteBuffer.allocate(4).putInt(extCrc32).array());
        out.write(payload);
        out.flush();
    }

    private static byte[] encodeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return new byte[0];
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (key == null || key.isBlank() || value == null) {
                continue;
            }
            String cleanKey = key.replace('\n', ' ').replace('\r', ' ').replace('=', '_').trim();
            String cleanVal = value.replace('\n', ' ').replace('\r', ' ').trim();
            if (!cleanKey.isEmpty() && !cleanVal.isEmpty()) {
                sb.append(cleanKey).append('=').append(cleanVal).append('\n');
            }
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, String> decodeMetadata(byte[] metaBytes) throws IOException {
        if (metaBytes.length == 0) {
            return Map.of();
        }
        Map<String, String> map = new LinkedHashMap<>();
        String text = new String(metaBytes, StandardCharsets.UTF_8);
        try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    throw new StreamCorruptedException("Malformed SMLM extended header metadata entry: " + line);
                }
                map.put(line.substring(0, eq), line.substring(eq + 1));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static SmlmHeader parseSmlmHeaderRest(PushbackInputStream pb, byte[] magic, String sourceName)
            throws IOException {
        byte[] rest = pb.readNBytes(SMLM_HEADER_SIZE - 4);
        if (rest.length < SMLM_HEADER_SIZE - 4) {
            throw new EOFException(String.format(
                    "Truncated SMLM envelope header in '%s' (expected %d bytes, got %d)",
                    sourceName, SMLM_HEADER_SIZE, 4 + rest.length));
        }
        ByteBuffer buf = ByteBuffer.wrap(rest);
        int formatVer = Short.toUnsignedInt(buf.getShort());
        int flags = Byte.toUnsignedInt(buf.get());
        long expectedCrc = Integer.toUnsignedLong(buf.getInt());

        CRC32 crc = new CRC32();
        crc.update(magic);
        crc.update(rest, 0, 3);
        long actualCrc = crc.getValue();
        if (actualCrc != expectedCrc) {
            throw new StreamCorruptedException(String.format(
                    "Corrupted SMLM envelope header in '%s': CRC-32 mismatch (expected 0x%08X, got 0x%08X)",
                    sourceName, expectedCrc, actualCrc));
        }
        if (formatVer < SMLM_MIN_VERSION || formatVer > SMLM_CURRENT_VERSION) {
            throw new StreamCorruptedException(String.format(
                    "Unsupported .sml format: '%s' is a v%d .sml; this runtime reads v%d–v%d",
                    sourceName, formatVer, SMLM_MIN_VERSION, SMLM_CURRENT_VERSION));
        }
        if ((flags & ~(SMLM_FLAG_EXTENDED_HEADER & 0xFF)) != 0) {
            throw new StreamCorruptedException(String.format(
                    "Unsupported SMLM header flags 0x%02X in '%s' (v%d allows 0x00 or 0x08)",
                    flags, sourceName, formatVer));
        }
        if ((flags & SMLM_FLAG_EXTENDED_HEADER) == 0) {
            return new SmlmHeader(formatVer, flags, actualCrc, false, -1L, 0L, Map.of(), SMLM_HEADER_SIZE);
        }

        // Parse extended header block: payloadSize (8B) + payloadCrc32 (4B) + metaLen (2B)
        byte[] extFixed = pb.readNBytes(14);
        if (extFixed.length < 14) {
            throw new EOFException(String.format(
                    "Truncated SMLM extended header in '%s' (expected 14 fixed bytes, got %d)",
                    sourceName, extFixed.length));
        }
        ByteBuffer extBuf = ByteBuffer.wrap(extFixed);
        long payloadSize = extBuf.getLong();
        long payloadCrc32 = Integer.toUnsignedLong(extBuf.getInt());
        int metaLen = Short.toUnsignedInt(extBuf.getShort());
        if (payloadSize <= 0 || payloadSize > DEFAULT_MAX_BYTES) {
            throw new StreamCorruptedException(String.format(
                    "Invalid SMLM payloadSize %d in '%s' (must be in 1..%d)",
                    payloadSize, sourceName, DEFAULT_MAX_BYTES));
        }
        if (metaLen > SMLM_MAX_METADATA_BYTES) {
            throw new StreamCorruptedException(String.format(
                    "SMLM metadata length %d exceeds maximum %d in '%s'",
                    metaLen, SMLM_MAX_METADATA_BYTES, sourceName));
        }
        byte[] metaBytes = pb.readNBytes(metaLen);
        if (metaBytes.length < metaLen) {
            throw new EOFException(String.format(
                    "Truncated SMLM extended header metadata in '%s' (expected %d bytes, got %d)",
                    sourceName, metaLen, metaBytes.length));
        }
        byte[] extCrcBytes = pb.readNBytes(4);
        if (extCrcBytes.length < 4) {
            throw new EOFException(String.format(
                    "Truncated SMLM extended header CRC in '%s' (expected 4 bytes, got %d)",
                    sourceName, extCrcBytes.length));
        }
        long expectedExtCrc = Integer.toUnsignedLong(ByteBuffer.wrap(extCrcBytes).getInt());
        CRC32 extCrc = new CRC32();
        extCrc.update(extFixed);
        extCrc.update(metaBytes);
        long actualExtCrc = extCrc.getValue();
        if (actualExtCrc != expectedExtCrc) {
            throw new StreamCorruptedException(String.format(
                    "Corrupted SMLM extended header in '%s': CRC-32 mismatch (expected 0x%08X, got 0x%08X)",
                    sourceName, expectedExtCrc, actualExtCrc));
        }
        Map<String, String> metadata = decodeMetadata(metaBytes);
        int totalHeaderBytes = SMLM_HEADER_SIZE + 14 + metaLen + 4;
        return new SmlmHeader(
                formatVer, flags, actualCrc, false, payloadSize, payloadCrc32, metadata, totalHeaderBytes);
    }

    /**
     * Stream wrapper that enforces exact {@code expectedSize} and {@code expectedCrc32}
     * over the serialized payload bytes and verifies that no unconsumed trailing bytes
     * remain when the stream is closed or reaches EOF.
     */
    private static final class PayloadVerifyingInputStream extends FilterInputStream {
        private final long expectedSize;
        private final long expectedCrc32;
        private final String sourceName;
        private final CRC32 crc = new CRC32();
        private long bytesRead = 0L;
        private boolean verified = false;

        PayloadVerifyingInputStream(InputStream in, long expectedSize, long expectedCrc32, String sourceName) {
            super(in);
            this.expectedSize = expectedSize;
            this.expectedCrc32 = expectedCrc32;
            this.sourceName = sourceName;
        }

        @Override
        public int read() throws IOException {
            int b = in.read();
            if (b == -1) {
                verifyAtEof();
                return -1;
            }
            bytesRead++;
            if (bytesRead > expectedSize) {
                throw new StreamCorruptedException(String.format(
                        "SMLM payload in '%s' exceeds declared payloadSize (%d bytes)",
                        sourceName, expectedSize));
            }
            crc.update(b);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            int n = in.read(b, off, len);
            if (n == -1) {
                verifyAtEof();
                return -1;
            }
            bytesRead += n;
            if (bytesRead > expectedSize) {
                throw new StreamCorruptedException(String.format(
                        "SMLM payload in '%s' exceeds declared payloadSize (%d bytes)",
                        sourceName, expectedSize));
            }
            crc.update(b, off, n);
            return n;
        }

        void verifyComplete() throws IOException {
            if (verified) {
                return;
            }
            // Drain any remaining buffered padding up to expectedSize + 1
            byte[] drain = new byte[512];
            while (bytesRead < expectedSize) {
                int toRead = (int) Math.min(drain.length, expectedSize - bytesRead);
                int n = in.read(drain, 0, toRead);
                if (n == -1) {
                    break;
                }
                bytesRead += n;
                crc.update(drain, 0, n);
            }
            // Check for unexpected trailing bytes after expectedSize
            if (in.read() != -1) {
                throw new StreamCorruptedException(String.format(
                        "SMLM payload in '%s' has trailing bytes beyond declared payloadSize (%d bytes)",
                        sourceName, expectedSize));
            }
            verifyAtEof();
        }

        private void verifyAtEof() throws IOException {
            if (verified) {
                return;
            }
            if (bytesRead != expectedSize) {
                throw new EOFException(String.format(
                        "Truncated SMLM payload in '%s': expected %d bytes, got %d",
                        sourceName, expectedSize, bytesRead));
            }
            long actualCrc = crc.getValue();
            if (actualCrc != expectedCrc32) {
                throw new StreamCorruptedException(String.format(
                        "Corrupted SMLM payload in '%s': payload CRC-32 mismatch (expected 0x%08X, got 0x%08X)",
                        sourceName, expectedCrc32, actualCrc));
            }
            verified = true;
        }
    }

    @Override
    public void close() throws IOException {
        try {
            if (payloadVerifier != null) {
                payloadVerifier.verifyComplete();
            }
        } finally {
            super.close();
        }
    }
}

