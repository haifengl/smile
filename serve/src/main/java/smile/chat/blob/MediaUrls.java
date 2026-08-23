/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE Serve is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE Serve is distributed in the hope that it will be useful,
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.chat.blob;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses serve media URLs of the form {@code /api/v1/media/{content_id}}.
 *
 * @author Haifeng Li
 */
public final class MediaUrls {
    /** Relative API path prefix (Quarkus rest path is {@code /api/v1}). */
    public static final String API_PATH_PREFIX = "/api/v1/media/";

    private static final Pattern CONTENT_ID = Pattern.compile(
            "(?:https?://[^/]+)?/api/v1/media/"
                    + "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})"
                    + "(?:[?#].*)?");

    private MediaUrls() {}

    /**
     * @param contentId opaque UUID.
     * @return relative media URL for chat completions and the UI.
     */
    public static String toUrl(String contentId) {
        return API_PATH_PREFIX + contentId;
    }

    /**
     * Extracts a content id from a relative or absolute serve media URL.
     *
     * @param url image/video URL from a content part.
     * @return content id, or empty when the URL is external / data / file.
     */
    public static Optional<String> parseContentId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        Matcher m = CONTENT_ID.matcher(url.trim());
        if (m.matches()) {
            return Optional.of(m.group(1).toLowerCase());
        }
        return Optional.empty();
    }

    /**
     * @param url candidate URL.
     * @return {@code true} when the URL points at this service's media API.
     */
    public static boolean isInternalMediaUrl(String url) {
        return parseContentId(url).isPresent();
    }
}
