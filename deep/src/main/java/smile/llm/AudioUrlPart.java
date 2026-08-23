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
 * Audio content referencing an HTTP(S) or {@code data:} URL.
 *
 * @param url audio URL (http(s) or data:audio/...;base64,...).
 * @author Haifeng Li
 */
public record AudioUrlPart(String url) implements ContentPart {
    /**
     * @param url audio URL — must not be null or blank.
     */
    public AudioUrlPart {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("audio url must not be blank");
        }
    }
}
