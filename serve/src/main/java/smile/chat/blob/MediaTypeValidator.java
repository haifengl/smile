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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Validates uploaded attachment MIME types against an explicit allowlist and
 * inspects leading magic bytes to block disguised/spoofed payloads and stored XSS.
 *
 * @author Haifeng Li
 */
public final class MediaTypeValidator {

    /** MIME types safe to render inline (browser raster images, audio, and video). */
    private static final Set<String> SAFE_INLINE_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/webp",
            "image/gif",
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "audio/mpeg",
            "audio/wav",
            "audio/ogg",
            "audio/mp4",
            "audio/flac",
            "audio/webm"
    );

    private static final Set<String> TEXT_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json"
    );

    private MediaTypeValidator() {}

    /**
     * Parses a comma-separated list of allowed MIME types into normalized tokens.
     *
     * @param csv configured comma-separated MIME types.
     * @return immutable set of normalized MIME types.
     */
    public static Set<String> parseAllowlist(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String part : csv.split(",")) {
            String normalized = normalizeMime(part);
            if (!normalized.isEmpty()) {
                set.add(normalized);
            }
        }
        return Set.copyOf(set);
    }

    /**
     * Strips MIME parameters (e.g. {@code ; charset=utf-8}) and lowercases.
     *
     * @param raw raw MIME type header value.
     * @return normalized base MIME type, or empty string if blank.
     */
    public static String normalizeMime(String raw) {
        if (raw == null) {
            return "";
        }
        int semi = raw.indexOf(';');
        String base = semi >= 0 ? raw.substring(0, semi) : raw;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Validates that the declared MIME type is in {@code allowedTypes} and that
     * the payload's magic bytes match the declared format.
     *
     * @param declaredMime client-declared Content-Type.
     * @param data         uploaded file bytes.
     * @param allowedTypes normalized set of allowed MIME types.
     * @return canonical normalized MIME type.
     * @throws IllegalArgumentException if the MIME type is disallowed or content bytes do not match.
     */
    public static String validate(String declaredMime, byte[] data, Set<String> allowedTypes) {
        String mime = normalizeMime(declaredMime);
        if (mime.isEmpty() || !allowedTypes.contains(mime)) {
            throw new IllegalArgumentException("Unsupported or disallowed media type: " + declaredMime);
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Empty upload is not permitted");
        }
        if (!matchesMagicBytes(mime, data)) {
            throw new IllegalArgumentException(
                    "Uploaded content does not match declared media type: " + mime);
        }
        return mime;
    }

    /**
     * Returns {@code true} only for browser-safe passive media formats that may be
     * served with {@code Content-Disposition: inline}. All other types must be served
     * as {@code attachment}.
     *
     * @param mime normalized or raw MIME type.
     * @return whether inline rendering is safe.
     */
    public static boolean isSafeInline(String mime) {
        return SAFE_INLINE_TYPES.contains(normalizeMime(mime));
    }

    /**
     * Sanitizes a filename for safe inclusion in {@code Content-Disposition}.
     * Strips directory traversal, path separators, control characters, quotes,
     * and semicolons.
     *
     * @param filename original client filename.
     * @param fallback fallback name (typically {@code content_id}) if blank after sanitization.
     * @return safe ASCII-friendly filename.
     */
    public static String sanitizeFilename(String filename, String fallback) {
        if (filename == null || filename.isBlank()) {
            return fallback;
        }
        // Keep only the final path segment (strip both Unix and Windows directories).
        String base = filename;
        int slash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder(Math.min(base.length(), 128));
        for (int i = 0; i < base.length() && sb.length() < 128; i++) {
            char c = base.charAt(i);
            if (c < 0x20 || c == 0x7F || c == '"' || c == ';' || c == '\\' || c == '/') {
                continue;
            }
            sb.append(c);
        }
        String cleaned = sb.toString().trim();
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1).trim();
        }
        if (cleaned.isEmpty() || cleaned.contains("..")) {
            return fallback;
        }
        return cleaned;
    }

    /**
     * Formats an RFC 6266 / RFC 5987 {@code Content-Disposition} header value.
     *
     * @param asAttachment {@code true} for {@code attachment}, {@code false} for {@code inline}.
     * @param filename     original or stored filename.
     * @param fallback     fallback identifier when filename is blank.
     * @return formatted header value.
     */
    public static String contentDisposition(boolean asAttachment, String filename, String fallback) {
        String safe = sanitizeFilename(filename, fallback);
        StringBuilder ascii = new StringBuilder(safe.length());
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            ascii.append((c >= 0x20 && c <= 0x7E) ? c : '_');
        }
        String encoded = URLEncoder.encode(safe, StandardCharsets.UTF_8).replace("+", "%20");
        return (asAttachment ? "attachment" : "inline")
                + "; filename=\"" + ascii + "\"; filename*=UTF-8''" + encoded;
    }

    private static boolean matchesMagicBytes(String mime, byte[] data) {
        return switch (mime) {
            case "image/png" -> startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "image/jpeg" -> startsWith(data, 0xFF, 0xD8, 0xFF);
            case "image/gif" -> startsWithAscii(data, 0, "GIF87a") || startsWithAscii(data, 0, "GIF89a");
            case "image/webp" -> data.length >= 12
                    && startsWithAscii(data, 0, "RIFF")
                    && startsWithAscii(data, 8, "WEBP");
            case "application/pdf" -> startsWithAscii(data, 0, "%PDF-");
            case "video/mp4", "audio/mp4", "video/quicktime" ->
                    data.length >= 12 && startsWithAscii(data, 4, "ftyp");
            case "video/webm", "audio/webm" -> startsWith(data, 0x1A, 0x45, 0xDF, 0xA3);
            case "audio/wav" -> data.length >= 12
                    && startsWithAscii(data, 0, "RIFF")
                    && startsWithAscii(data, 8, "WAVE");
            case "audio/ogg" -> startsWithAscii(data, 0, "OggS");
            case "audio/flac" -> startsWithAscii(data, 0, "fLaC");
            case "audio/mpeg" -> startsWithAscii(data, 0, "ID3")
                    || (data.length >= 2 && data[0] == (byte) 0xFF && (data[1] & 0xE0) == 0xE0);
            default -> {
                if (TEXT_TYPES.contains(mime)) {
                    yield hasNoNulBytes(data);
                }
                // Operator-configured custom MIME type outside built-in signature table.
                yield true;
            }
        };
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWithAscii(byte[] data, int offset, String ascii) {
        if (data.length < offset + ascii.length()) {
            return false;
        }
        for (int i = 0; i < ascii.length(); i++) {
            if (data[offset + i] != (byte) ascii.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNoNulBytes(byte[] data) {
        int checkLen = Math.min(data.length, 8192);
        for (int i = 0; i < checkLen; i++) {
            if (data[i] == 0) {
                return false;
            }
        }
        return true;
    }
}
