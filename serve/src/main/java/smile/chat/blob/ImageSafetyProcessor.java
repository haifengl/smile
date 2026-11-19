/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with SMILE Serve. If not, see
 * <https://www.gnu.org/licenses/>.
 */
package smile.chat.blob;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Header-only image decompression-bomb guard and lossless EXIF/GPS/XMP metadata
 * stripper for uploaded raster images ({@code image/png}, {@code image/jpeg},
 * {@code image/gif}, {@code image/webp}).
 *
 * <p>Image dimensions are parsed strictly from container headers without allocating
 * any pixel buffers or invoking {@code ImageIO.read}, protecting the JVM and downstream
 * vision-language models from decompression bombs. Metadata stripping operates
 * losslessly on container chunks/segments (preserving ICC color profiles and image
 * pixel data verbatim).
 *
 * @author Haifeng Li
 */
public final class ImageSafetyProcessor {
    private static final Set<String> PNG_METADATA_CHUNKS = Set.of(
            "eXIf", "tEXt", "zTXt", "iTXt", "tIME");

    private static final byte[] ICC_PROFILE_PREFIX =
            "ICC_PROFILE\0".getBytes(StandardCharsets.US_ASCII);

    private ImageSafetyProcessor() {}

    /**
     * Parsed image dimensions.
     *
     * @param width  width in pixels.
     * @param height height in pixels.
     */
    public record Dimensions(int width, int height) {
        /** @return total pixel count ({@code width * height}). */
        public long pixels() {
            return (long) width * (long) height;
        }
    }

    /**
     * Validates image dimensions against decompression-bomb limits and optionally
     * strips EXIF/GPS/XMP/comment metadata losslessly.
     *
     * <p>Non-image MIME types are returned unchanged.
     *
     * @param mimeType      verified canonical MIME type.
     * @param data          uploaded file bytes.
     * @param maxPixels     maximum total pixel count ({@code <= 0} disables).
     * @param maxDimension  maximum width or height ({@code <= 0} disables).
     * @param stripMetadata whether to strip EXIF/GPS/XMP/comment metadata.
     * @return sanitized bytes (may be the original array if unchanged).
     * @throws IllegalArgumentException if the image header is malformed or exceeds bounds.
     */
    public static byte[] validateAndSanitize(String mimeType,
                                             byte[] data,
                                             long maxPixels,
                                             int maxDimension,
                                             boolean stripMetadata) {
        if (mimeType == null || !mimeType.startsWith("image/")) {
            return data;
        }
        Dimensions dims = readDimensions(mimeType, data);
        if (dims.width() <= 0 || dims.height() <= 0) {
            throw new IllegalArgumentException("Invalid image dimensions: "
                    + dims.width() + "x" + dims.height());
        }
        if (maxDimension > 0 && (dims.width() > maxDimension || dims.height() > maxDimension)) {
            throw new IllegalArgumentException("Image dimension " + dims.width() + "x" + dims.height()
                    + " exceeds limit of " + maxDimension + "px");
        }
        if (maxPixels > 0 && dims.pixels() > maxPixels) {
            throw new IllegalArgumentException("Image pixel count " + dims.pixels()
                    + " (" + dims.width() + "x" + dims.height() + ") exceeds limit of " + maxPixels);
        }
        if (!stripMetadata) {
            return data;
        }
        return switch (mimeType) {
            case "image/png" -> stripPngMetadata(data);
            case "image/jpeg" -> stripJpegMetadata(data);
            case "image/webp" -> stripWebpMetadata(data);
            default -> data;
        };
    }

    /**
     * Extracts image dimensions from container headers without decoding pixels.
     *
     * @param mimeType canonical image MIME type.
     * @param data     image bytes.
     * @return parsed width and height.
     * @throws IllegalArgumentException if the image header is unsupported or malformed.
     */
    public static Dimensions readDimensions(String mimeType, byte[] data) {
        if (data == null || data.length < 10) {
            throw new IllegalArgumentException("Image payload is truncated");
        }
        return switch (mimeType) {
            case "image/png" -> readPngDimensions(data);
            case "image/jpeg" -> readJpegDimensions(data);
            case "image/gif" -> readGifDimensions(data);
            case "image/webp" -> readWebpDimensions(data);
            default -> throw new IllegalArgumentException("Unsupported image type for dimension check: " + mimeType);
        };
    }

    private static Dimensions readPngDimensions(byte[] data) {
        if (data.length < 24) {
            throw new IllegalArgumentException("Truncated PNG header");
        }
        int ihdrLen = u32be(data, 8);
        if (ihdrLen != 13
                || data[12] != 'I' || data[13] != 'H' || data[14] != 'D' || data[15] != 'R') {
            throw new IllegalArgumentException("PNG missing valid IHDR chunk");
        }
        int width = u32be(data, 16);
        int height = u32be(data, 20);
        return new Dimensions(width, height);
    }

    private static Dimensions readJpegDimensions(byte[] data) {
        if (data.length < 4 || (data[0] & 0xFF) != 0xFF || (data[1] & 0xFF) != 0xD8) {
            throw new IllegalArgumentException("Invalid JPEG SOI marker");
        }
        int pos = 2;
        while (pos < data.length) {
            if ((data[pos] & 0xFF) != 0xFF) {
                throw new IllegalArgumentException("Malformed JPEG marker stream at offset " + pos);
            }
            while (pos < data.length && (data[pos] & 0xFF) == 0xFF) {
                pos++;
            }
            if (pos >= data.length) {
                break;
            }
            int marker = data[pos++] & 0xFF;
            if (marker == 0xD9 || marker == 0xDA) {
                break;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                continue;
            }
            if (pos + 2 > data.length) {
                throw new IllegalArgumentException("Truncated JPEG segment length");
            }
            int segLen = u16be(data, pos);
            if (segLen < 2 || pos + segLen > data.length) {
                throw new IllegalArgumentException("Invalid JPEG segment length: " + segLen);
            }
            if (isSofMarker(marker)) {
                if (segLen < 7) {
                    throw new IllegalArgumentException("Truncated JPEG SOF segment");
                }
                int height = u16be(data, pos + 3);
                int width = u16be(data, pos + 5);
                return new Dimensions(width, height);
            }
            pos += segLen;
        }
        throw new IllegalArgumentException("JPEG missing Start-Of-Frame (SOF) dimensions");
    }

    private static Dimensions readGifDimensions(byte[] data) {
        if (data.length < 10) {
            throw new IllegalArgumentException("Truncated GIF header");
        }
        int width = u16le(data, 6);
        int height = u16le(data, 8);
        return new Dimensions(width, height);
    }

    private static Dimensions readWebpDimensions(byte[] data) {
        if (data.length < 20) {
            throw new IllegalArgumentException("Truncated WebP header");
        }
        int pos = 12;
        while (pos + 8 <= data.length) {
            String fourCc = new String(data, pos, 4, StandardCharsets.US_ASCII);
            long size = u32leUnsigned(data, pos + 4);
            int payload = pos + 8;
            if (size < 0 || payload + size > data.length) {
                throw new IllegalArgumentException("Malformed WebP chunk: " + fourCc);
            }
            switch (fourCc) {
                case "VP8X" -> {
                    if (size < 10) {
                        throw new IllegalArgumentException("Truncated WebP VP8X chunk");
                    }
                    int width = 1 + u24le(data, payload + 4);
                    int height = 1 + u24le(data, payload + 7);
                    return new Dimensions(width, height);
                }
                case "VP8 " -> {
                    if (size < 10
                            || (data[payload + 3] & 0xFF) != 0x9D
                            || (data[payload + 4] & 0xFF) != 0x01
                            || (data[payload + 5] & 0xFF) != 0x2A) {
                        throw new IllegalArgumentException("Invalid WebP VP8 frame header");
                    }
                    int width = u16le(data, payload + 6) & 0x3FFF;
                    int height = u16le(data, payload + 8) & 0x3FFF;
                    return new Dimensions(width, height);
                }
                case "VP8L" -> {
                    if (size < 5 || (data[payload] & 0xFF) != 0x2F) {
                        throw new IllegalArgumentException("Invalid WebP VP8L header");
                    }
                    int bits = u32le(data, payload + 1);
                    int width = (bits & 0x3FFF) + 1;
                    int height = ((bits >>> 14) & 0x3FFF) + 1;
                    return new Dimensions(width, height);
                }
                default -> {
                    // continue scanning sub-chunks
                }
            }
            long padded = size + (size & 1L);
            if (payload + padded > data.length) {
                break;
            }
            pos = (int) (payload + padded);
        }
        throw new IllegalArgumentException("WebP missing VP8/VP8L/VP8X dimensions");
    }

    /**
     * Losslessly removes PNG metadata chunks ({@code eXIf}, {@code tEXt}, {@code zTXt},
     * {@code iTXt}, {@code tIME}) while retaining pixel data and color profile chunks.
     */
    static byte[] stripPngMetadata(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(data, 0, 8);
        boolean stripped = false;
        int pos = 8;
        while (pos + 12 <= data.length) {
            int len = u32be(data, pos);
            if (len < 0 || (long) pos + 12L + len > data.length) {
                throw new IllegalArgumentException("Malformed PNG chunk at offset " + pos);
            }
            String type = new String(data, pos + 4, 4, StandardCharsets.US_ASCII);
            int total = 12 + len;
            if (PNG_METADATA_CHUNKS.contains(type)) {
                stripped = true;
            } else {
                out.write(data, pos, total);
            }
            pos += total;
            if ("IEND".equals(type)) {
                break;
            }
        }
        return stripped ? out.toByteArray() : data;
    }

    /**
     * Losslessly removes JPEG metadata segments ({@code APP1} EXIF/GPS/XMP,
     * non-ICC {@code APP2}, {@code APP12}, {@code APP13} IPTC/Photoshop, {@code COM})
     * while preserving {@code JFIF}, {@code ICC_PROFILE}, {@code Adobe} APP14,
     * quantization/Huffman tables, SOF, and scan data verbatim.
     */
    static byte[] stripJpegMetadata(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        out.write(0xFF);
        out.write(0xD8);
        boolean stripped = false;
        int pos = 2;
        while (pos < data.length) {
            if ((data[pos] & 0xFF) != 0xFF) {
                throw new IllegalArgumentException("Malformed JPEG marker stream at offset " + pos);
            }
            while (pos < data.length && (data[pos] & 0xFF) == 0xFF) {
                pos++;
            }
            if (pos >= data.length) {
                break;
            }
            int marker = data[pos++] & 0xFF;
            if (marker == 0xDA) {
                // SOS: copy marker and entire entropy-coded scan through EOI verbatim.
                out.write(0xFF);
                out.write(marker);
                out.write(data, pos, data.length - pos);
                break;
            }
            if (marker == 0xD9) {
                out.write(0xFF);
                out.write(marker);
                break;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                out.write(0xFF);
                out.write(marker);
                continue;
            }
            if (pos + 2 > data.length) {
                throw new IllegalArgumentException("Truncated JPEG segment length");
            }
            int segLen = u16be(data, pos);
            if (segLen < 2 || pos + segLen > data.length) {
                throw new IllegalArgumentException("Invalid JPEG segment length: " + segLen);
            }
            boolean drop = switch (marker) {
                case 0xE1, 0xEC, 0xED, 0xFE -> true;
                case 0xE2 -> !startsWithAt(data, pos + 2, segLen - 2, ICC_PROFILE_PREFIX);
                default -> false;
            };
            if (drop) {
                stripped = true;
            } else {
                out.write(0xFF);
                out.write(marker);
                out.write(data, pos, segLen);
            }
            pos += segLen;
        }
        return stripped ? out.toByteArray() : data;
    }

    /**
     * Losslessly removes {@code EXIF} and {@code XMP } chunks from WebP RIFF containers,
     * clears corresponding {@code VP8X} flag bits, and updates the RIFF size header.
     */
    static byte[] stripWebpMetadata(byte[] data) {
        ByteArrayOutputStream chunks = new ByteArrayOutputStream(data.length);
        boolean stripped = false;
        int vp8xFlagsOffset = -1;
        int pos = 12;
        while (pos + 8 <= data.length) {
            String fourCc = new String(data, pos, 4, StandardCharsets.US_ASCII);
            long size = u32leUnsigned(data, pos + 4);
            int payload = pos + 8;
            if (size < 0 || payload + size > data.length) {
                throw new IllegalArgumentException("Malformed WebP chunk: " + fourCc);
            }
            long padded = size + (size & 1L);
            int total = (int) Math.min(8L + padded, data.length - pos);
            if ("EXIF".equals(fourCc) || "XMP ".equals(fourCc)) {
                stripped = true;
            } else {
                if ("VP8X".equals(fourCc) && size >= 10) {
                    vp8xFlagsOffset = chunks.size() + 8;
                }
                chunks.write(data, pos, total);
                if ((size & 1L) == 1L && total == 8 + size) {
                    chunks.write(0);
                }
            }
            if (payload + padded > data.length) {
                break;
            }
            pos = (int) (payload + padded);
        }
        if (!stripped) {
            return data;
        }
        byte[] body = chunks.toByteArray();
        if (vp8xFlagsOffset >= 0 && vp8xFlagsOffset < body.length) {
            // Clear EXIF (bit 3 = 0x08) and XMP (bit 2 = 0x04) feature flags in VP8X.
            body[vp8xFlagsOffset] = (byte) (body[vp8xFlagsOffset] & ~0x0C);
        }
        int riffSize = 4 + body.length;
        byte[] result = new byte[12 + body.length];
        result[0] = 'R';
        result[1] = 'I';
        result[2] = 'F';
        result[3] = 'F';
        result[4] = (byte) (riffSize & 0xFF);
        result[5] = (byte) ((riffSize >>> 8) & 0xFF);
        result[6] = (byte) ((riffSize >>> 16) & 0xFF);
        result[7] = (byte) ((riffSize >>> 24) & 0xFF);
        result[8] = 'W';
        result[9] = 'E';
        result[10] = 'B';
        result[11] = 'P';
        System.arraycopy(body, 0, result, 12, body.length);
        return result;
    }

    private static boolean isSofMarker(int marker) {
        return marker >= 0xC0 && marker <= 0xCF
                && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    private static boolean startsWithAt(byte[] data, int offset, int length, byte[] prefix) {
        if (length < prefix.length || offset + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int u16be(byte[] data, int off) {
        return ((data[off] & 0xFF) << 8) | (data[off + 1] & 0xFF);
    }

    private static int u16le(byte[] data, int off) {
        return (data[off] & 0xFF) | ((data[off + 1] & 0xFF) << 8);
    }

    private static int u24le(byte[] data, int off) {
        return (data[off] & 0xFF)
                | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16);
    }

    private static int u32be(byte[] data, int off) {
        return ((data[off] & 0xFF) << 24)
                | ((data[off + 1] & 0xFF) << 16)
                | ((data[off + 2] & 0xFF) << 8)
                | (data[off + 3] & 0xFF);
    }

    private static int u32le(byte[] data, int off) {
        return (data[off] & 0xFF)
                | ((data[off + 1] & 0xFF) << 8)
                | ((data[off + 2] & 0xFF) << 16)
                | ((data[off + 3] & 0xFF) << 24);
    }

    private static long u32leUnsigned(byte[] data, int off) {
        return Integer.toUnsignedLong(u32le(data, off));
    }
}
