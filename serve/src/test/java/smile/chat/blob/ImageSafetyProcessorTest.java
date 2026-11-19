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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ImageSafetyProcessor} covering header-only decompression-bomb
 * protection and lossless EXIF/GPS/XMP metadata stripping across PNG, JPEG, GIF, and WebP.
 */
public class ImageSafetyProcessorTest {

    @Test
    public void testPngDimensionsBombRejectionAndMetadataStripping() {
        byte[] cleanPng = buildPng(64, 32, false, false);
        ImageSafetyProcessor.Dimensions dims = ImageSafetyProcessor.readDimensions("image/png", cleanPng);
        assertEquals(64, dims.width());
        assertEquals(32, dims.height());
        assertEquals(2048L, dims.pixels());

        // Clean PNG with no metadata is returned unchanged
        byte[] same = ImageSafetyProcessor.validateAndSanitize("image/png", cleanPng, 10_000, 1000, true);
        assertArrayEquals(cleanPng, same);

        // PNG with eXIf, tEXt (GPS), and sRGB strips eXIf/tEXt while preserving sRGB + IDAT + IEND
        byte[] dirtyPng = buildPng(64, 32, true, true);
        assertTrue(containsAscii(dirtyPng, "GPSLatitude"));
        assertTrue(containsAscii(dirtyPng, "sRGB"));
        byte[] stripped = ImageSafetyProcessor.validateAndSanitize("image/png", dirtyPng, 10_000, 1000, true);
        assertFalse(containsAscii(stripped, "GPSLatitude"), "tEXt/eXIf metadata must be stripped");
        assertFalse(containsAscii(stripped, "eXIf"), "eXIf chunk must be stripped");
        assertTrue(containsAscii(stripped, "sRGB"), "safe color profile chunk sRGB must be preserved");
        assertTrue(containsAscii(stripped, "IDAT"), "pixel data chunk must be preserved");
        assertTrue(containsAscii(stripped, "IEND"), "IEND trailer must be preserved");
        assertEquals(64, ImageSafetyProcessor.readDimensions("image/png", stripped).width());

        // Decompression bomb: huge declared dimensions in IHDR rejected without allocating pixels
        byte[] bombPng = buildPng(20_000, 20_000, false, false);
        assertThrows(IllegalArgumentException.class, () ->
                ImageSafetyProcessor.validateAndSanitize("image/png", bombPng, 67_108_864L, 16_384, true));
        assertThrows(IllegalArgumentException.class, () ->
                ImageSafetyProcessor.validateAndSanitize("image/png", bombPng, 1_000_000L, 0, false));
    }

    @Test
    public void testJpegDimensionsBombRejectionAndMetadataStripping() {
        byte[] jpeg = buildJpeg(800, 600, true, true, true);
        ImageSafetyProcessor.Dimensions dims = ImageSafetyProcessor.readDimensions("image/jpeg", jpeg);
        assertEquals(800, dims.width());
        assertEquals(600, dims.height());

        assertTrue(containsAscii(jpeg, "Exif"));
        assertTrue(containsAscii(jpeg, "GPS_SECRET"));
        assertTrue(containsAscii(jpeg, "SECRET_COMMENT"));
        assertTrue(containsAscii(jpeg, "ICC_PROFILE"));

        byte[] stripped = ImageSafetyProcessor.validateAndSanitize("image/jpeg", jpeg, 10_000_000L, 4096, true);
        assertFalse(containsAscii(stripped, "Exif"), "APP1 EXIF must be stripped");
        assertFalse(containsAscii(stripped, "GPS_SECRET"), "APP1 payload must be stripped");
        assertFalse(containsAscii(stripped, "SECRET_COMMENT"), "COM comment must be stripped");
        assertTrue(containsAscii(stripped, "JFIF"), "APP0 JFIF must be preserved");
        assertTrue(containsAscii(stripped, "ICC_PROFILE"), "APP2 ICC_PROFILE must be preserved");
        assertTrue(containsAscii(stripped, "SCAN_PAYLOAD"), "SOS scan payload must be preserved verbatim");
        assertEquals(800, ImageSafetyProcessor.readDimensions("image/jpeg", stripped).width());
        assertEquals(600, ImageSafetyProcessor.readDimensions("image/jpeg", stripped).height());

        // Decompression bomb JPEG (12000 x 12000 = 144 MP > 64 MP)
        byte[] bombJpeg = buildJpeg(12_000, 12_000, false, false, false);
        assertThrows(IllegalArgumentException.class, () ->
                ImageSafetyProcessor.validateAndSanitize("image/jpeg", bombJpeg, 67_108_864L, 16_384, true));
    }

    @Test
    public void testGifDimensionsAndBombRejection() {
        byte[] gif = new byte[] {
                'G', 'I', 'F', '8', '9', 'a',
                (byte) 0x40, (byte) 0x01, // width = 320 LE
                (byte) 0xF0, (byte) 0x00, // height = 240 LE
                0x00, 0x00, 0x00
        };
        ImageSafetyProcessor.Dimensions dims = ImageSafetyProcessor.readDimensions("image/gif", gif);
        assertEquals(320, dims.width());
        assertEquals(240, dims.height());
        assertArrayEquals(gif, ImageSafetyProcessor.validateAndSanitize("image/gif", gif, 1_000_000L, 1024, true));

        byte[] bombGif = new byte[] {
                'G', 'I', 'F', '8', '9', 'a',
                (byte) 0x00, (byte) 0x40, // width = 16384
                (byte) 0x00, (byte) 0x40, // height = 16384 (268 MP)
                0x00, 0x00, 0x00
        };
        assertThrows(IllegalArgumentException.class, () ->
                ImageSafetyProcessor.validateAndSanitize("image/gif", bombGif, 67_108_864L, 16_384, true));
    }

    @Test
    public void testWebpVp8xDimensionsBombAndExifXmpStripping() {
        byte[] webp = buildWebpVp8x(1024, 768, true);
        ImageSafetyProcessor.Dimensions dims = ImageSafetyProcessor.readDimensions("image/webp", webp);
        assertEquals(1024, dims.width());
        assertEquals(768, dims.height());
        assertTrue(containsAscii(webp, "GPS_WEBP_TAG"));
        assertTrue(containsAscii(webp, "XMP_SECRET"));

        byte[] stripped = ImageSafetyProcessor.validateAndSanitize("image/webp", webp, 10_000_000L, 4096, true);
        assertFalse(containsAscii(stripped, "GPS_WEBP_TAG"), "EXIF chunk must be stripped");
        assertFalse(containsAscii(stripped, "XMP_SECRET"), "XMP chunk must be stripped");
        assertTrue(containsAscii(stripped, "VP8X"), "VP8X header preserved");
        // Verify VP8X EXIF (0x08) and XMP (0x04) flag bits were cleared
        assertEquals(0, stripped[20] & 0x0C, "VP8X EXIF/XMP flags must be cleared");
        // Verify updated RIFF chunk size matches stripped.length - 8
        int riffSize = (stripped[4] & 0xFF)
                | ((stripped[5] & 0xFF) << 8)
                | ((stripped[6] & 0xFF) << 16)
                | ((stripped[7] & 0xFF) << 24);
        assertEquals(stripped.length - 8, riffSize);
        assertEquals(1024, ImageSafetyProcessor.readDimensions("image/webp", stripped).width());
        assertEquals(768, ImageSafetyProcessor.readDimensions("image/webp", stripped).height());

        // Decompression bomb WebP (20000 x 10000)
        byte[] bombWebp = buildWebpVp8x(20_000, 10_000, false);
        assertThrows(IllegalArgumentException.class, () ->
                ImageSafetyProcessor.validateAndSanitize("image/webp", bombWebp, 67_108_864L, 16_384, true));
    }

    @Test
    public void testNonImagePassesThroughUnchanged() {
        byte[] txt = "hello world".getBytes(StandardCharsets.UTF_8);
        assertSame(txt, ImageSafetyProcessor.validateAndSanitize("text/plain", txt, 10, 10, true));
    }

    // ---- Test fixture builders ----

    public static byte[] buildPng(int width, int height, boolean withMetadata, boolean withSrgb) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        byte[] ihdr = new byte[13];
        putBe32(ihdr, 0, width);
        putBe32(ihdr, 4, height);
        ihdr[8] = 8; // bit depth
        ihdr[9] = 2; // RGB
        writePngChunk(out, "IHDR", ihdr);
        if (withSrgb) {
            writePngChunk(out, "sRGB", new byte[] {0});
        }
        if (withMetadata) {
            writePngChunk(out, "eXIf", "Exif\0\0GPS_COORDS".getBytes(StandardCharsets.UTF_8));
            writePngChunk(out, "tEXt", "GPSLatitude\0 37.7749N".getBytes(StandardCharsets.UTF_8));
        }
        writePngChunk(out, "IDAT", new byte[] {0x78, (byte) 0x9C, 0x03, 0x00, 0x00, 0x00, 0x00, 0x01});
        writePngChunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void writePngChunk(ByteArrayOutputStream out, String type, byte[] payload) {
        byte[] len = new byte[4];
        putBe32(len, 0, payload.length);
        out.writeBytes(len);
        out.writeBytes(type.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(payload);
        out.writeBytes(new byte[4]); // dummy CRC
    }

    public static byte[] buildJpeg(int width, int height, boolean withExif, boolean withIcc, boolean withComment) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8); // SOI
        // APP0 JFIF
        writeJpegSegment(out, 0xE0, "JFIF\0\1\1\0\0\1\0\1\0\0".getBytes(StandardCharsets.ISO_8859_1));
        if (withExif) {
            writeJpegSegment(out, 0xE1, "Exif\0\0GPS_SECRET_37.7749".getBytes(StandardCharsets.US_ASCII));
        }
        if (withIcc) {
            writeJpegSegment(out, 0xE2, "ICC_PROFILE\0\1\1profile".getBytes(StandardCharsets.ISO_8859_1));
        }
        if (withComment) {
            writeJpegSegment(out, 0xFE, "SECRET_COMMENT".getBytes(StandardCharsets.US_ASCII));
        }
        // SOF0
        byte[] sof = new byte[11];
        sof[0] = 8; // precision
        sof[1] = (byte) ((height >>> 8) & 0xFF);
        sof[2] = (byte) (height & 0xFF);
        sof[3] = (byte) ((width >>> 8) & 0xFF);
        sof[4] = (byte) (width & 0xFF);
        sof[5] = 1; // components
        writeJpegSegment(out, 0xC0, sof);
        // SOS + scan payload + EOI
        out.write(0xFF);
        out.write(0xDA);
        out.writeBytes("SCAN_PAYLOAD".getBytes(StandardCharsets.US_ASCII));
        out.write(0xFF);
        out.write(0xD9);
        return out.toByteArray();
    }

    private static void writeJpegSegment(ByteArrayOutputStream out, int marker, byte[] payload) {
        int segLen = payload.length + 2;
        out.write(0xFF);
        out.write(marker);
        out.write((segLen >>> 8) & 0xFF);
        out.write(segLen & 0xFF);
        out.writeBytes(payload);
    }

    private static byte[] buildWebpVp8x(int width, int height, boolean withMetadata) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] vp8x = new byte[10];
        vp8x[0] = (byte) (withMetadata ? 0x0C : 0x00); // EXIF | XMP flags
        int wMinus1 = width - 1;
        int hMinus1 = height - 1;
        vp8x[4] = (byte) (wMinus1 & 0xFF);
        vp8x[5] = (byte) ((wMinus1 >>> 8) & 0xFF);
        vp8x[6] = (byte) ((wMinus1 >>> 16) & 0xFF);
        vp8x[7] = (byte) (hMinus1 & 0xFF);
        vp8x[8] = (byte) ((hMinus1 >>> 8) & 0xFF);
        vp8x[9] = (byte) ((hMinus1 >>> 16) & 0xFF);
        writeRiffChunk(body, "VP8X", vp8x);
        writeRiffChunk(body, "VP8 ", new byte[] {0, 0, 0, (byte) 0x9D, 0x01, 0x2A, 1, 0, 1, 0});
        if (withMetadata) {
            writeRiffChunk(body, "EXIF", "GPS_WEBP_TAG".getBytes(StandardCharsets.US_ASCII));
            writeRiffChunk(body, "XMP ", "XMP_SECRET".getBytes(StandardCharsets.US_ASCII));
        }
        byte[] chunks = body.toByteArray();
        ByteArrayOutputStream riff = new ByteArrayOutputStream(12 + chunks.length);
        riff.writeBytes("RIFF".getBytes(StandardCharsets.US_ASCII));
        byte[] sizeLe = new byte[4];
        putLe32(sizeLe, 0, 4 + chunks.length);
        riff.writeBytes(sizeLe);
        riff.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        riff.writeBytes(chunks);
        return riff.toByteArray();
    }

    private static void writeRiffChunk(ByteArrayOutputStream out, String fourCc, byte[] payload) {
        out.writeBytes(fourCc.getBytes(StandardCharsets.US_ASCII));
        byte[] sizeLe = new byte[4];
        putLe32(sizeLe, 0, payload.length);
        out.writeBytes(sizeLe);
        out.writeBytes(payload);
        if ((payload.length & 1) == 1) {
            out.write(0);
        }
    }

    private static void putBe32(byte[] buf, int off, int v) {
        buf[off] = (byte) ((v >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((v >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((v >>> 8) & 0xFF);
        buf[off + 3] = (byte) (v & 0xFF);
    }

    private static void putLe32(byte[] buf, int off, int v) {
        buf[off] = (byte) (v & 0xFF);
        buf[off + 1] = (byte) ((v >>> 8) & 0xFF);
        buf[off + 2] = (byte) ((v >>> 16) & 0xFF);
        buf[off + 3] = (byte) ((v >>> 24) & 0xFF);
    }

    private static boolean containsAscii(byte[] data, String needle) {
        return new String(data, StandardCharsets.ISO_8859_1).contains(needle);
    }
}
