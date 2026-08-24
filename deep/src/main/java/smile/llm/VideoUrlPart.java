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
package smile.llm;

/**
 * Video content referencing an HTTP(S) or file URL.
 *
 * @param url video URL.
 * @param fps optional frame-sampling rate; {@code null} uses processor default.
 * @author Haifeng Li
 */
public record VideoUrlPart(String url, Double fps) implements ContentPart {
    /**
     * @param url video URL — must not be null or blank.
     * @param fps sampling fps, or {@code null} for default.
     */
    public VideoUrlPart {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("video url must not be blank");
        }
        if (fps != null && fps <= 0) {
            throw new IllegalArgumentException("fps must be positive");
        }
    }

    /**
     * Video part with default fps.
     *
     * @param url video URL.
     */
    public VideoUrlPart(String url) {
        this(url, null);
    }
}
