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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectStreamConstants;
import java.io.PushbackInputStream;
import java.io.StreamCorruptedException;
import java.nio.ByteBuffer;
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
     * The default allow-list pattern accepted by every instance.
     *
     * <ul>
     *   <li>{@code smile.**}            – all SMILE classes</li>
     *   <li>{@code java.lang.**}        – String, Number, Enum, etc.</li>
     *   <li>{@code java.util.**}        – Collection, Map, Optional, …</li>
     *   <li>{@code java.math.**}        – BigInteger, BigDecimal</li>
     *   <li>{@code java.time.**}        – LocalDate, Instant, …</li>
     *   <li>{@code [*}                  – any array type</li>
     *   <li>{@code !*}                  – reject everything else</li>
     * </ul>
     */
    public static final ObjectInputFilter DEFAULT_FILTER =
            ObjectInputFilter.Config.createFilter(
                    "smile.**" +
                    ";java.lang.**" +
                    ";java.util.**" +
                    ";java.math.**" +
                    ";java.time.**" +
                    ";[*" +
                    ";!*");

    /** ASCII magic {@code "SMLM"} ({@code 0x534D4C4D}) for enveloped SMILE model files. */
    public static final int SMLM_MAGIC = 0x534D4C4D;
    /** Minimum supported {@code SMLM} envelope format version. */
    public static final short SMLM_MIN_VERSION = 1;
    /** Current and maximum supported {@code SMLM} envelope format version. */
    public static final short SMLM_CURRENT_VERSION = 1;
    /** Fixed byte length of the {@code SMLM} envelope header. */
    public static final int SMLM_HEADER_SIZE = 11;
    /** Legacy Java {@link ObjectOutputStream} stream magic + version ({@code 0xACED0005}). */
    public static final int LEGACY_STREAM_HEADER =
            ((Short.toUnsignedInt(ObjectStreamConstants.STREAM_MAGIC) << 16)
                    | Short.toUnsignedInt(ObjectStreamConstants.STREAM_VERSION));

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
    public record SmlmHeader(int version, int flags, long crc32, boolean legacy) {
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
        super(preStripped ? payloadStream : stripSmlmHeaderIfPresent(payloadStream));
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
     * 11-byte {@code SMLM} model envelope header, validates the format version,
     * flags, and CRC-32 before returning the stream positioned at the Java
     * serialization payload; otherwise pushes the bytes back so raw
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
        parseSmlmHeaderRest(pb, magic, "<stream>");
        return pb;
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
        if (flags != 0) {
            throw new StreamCorruptedException(String.format(
                    "Unsupported SMLM header flags 0x%02X in '%s' (v%d requires 0x00)",
                    flags, sourceName, formatVer));
        }
        return new SmlmHeader(formatVer, flags, actualCrc, false);
    }
}

