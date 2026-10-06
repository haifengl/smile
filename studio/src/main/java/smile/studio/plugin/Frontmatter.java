/*
 * Copyright (c) 2026 Haifeng Li. All rights reserved.
 *
 * SPDX-License-Identifier: BUSL-1.1
 *
 * This software is licensed under the Business Source License version 1.1 (BSL 1.1).
 * Use of this work is governed by the BSL 1.1 terms and conditions set forth in
 * the studio/LICENSE file (or LICENSE file in standalone distributions) and at
 * https://mariadb.com/bsl11.
 *
 * Use of this work is strictly for evaluation and/or non-production purposes.
 * For commercial production use, please contact sales@aihalo.dev.
 *
 * Effective on the Change Date (four years from the first publication of this
 * version), this file automatically converts to the GNU Affero General Public
 * License version 3.0 (AGPLv3) or later.
 */
package smile.studio.plugin;

import java.util.List;

/**
 * Builds the YAML frontmatter block of a translated {@code AGENT.md} or
 * {@code SKILL.md}.
 *
 * <p>Only the subset of YAML that {@code ioa}'s {@code Memory} parser actually
 * reads is emitted — scalars and flow lists — so the output is deterministic and
 * needs no YAML library. Values are single-quoted with internal quotes doubled,
 * which is valid YAML for any string.
 *
 * @author Haifeng Li
 */
final class Frontmatter {

    private final StringBuilder sb = new StringBuilder("---\n");

    /**
     * Adds a scalar field. Blank values are omitted so the file stays clean.
     * @param key the field name.
     * @param value the value, or null to omit.
     * @return this builder.
     */
    Frontmatter put(String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(key).append(": ").append(quote(value)).append('\n');
        }
        return this;
    }

    /**
     * Adds a string-list field. An empty list is omitted.
     * @param key the field name.
     * @param values the values.
     * @return this builder.
     */
    Frontmatter putList(String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            sb.append(key).append(": [");
            for (int i = 0; i < values.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(quote(values.get(i)));
            }
            sb.append("]\n");
        }
        return this;
    }

    /**
     * Adds a raw already-formatted line, used for a value copied verbatim from the
     * source manifest (for example a multi-line description).
     * @param rawLine the line, without a trailing newline.
     * @return this builder.
     */
    Frontmatter putRaw(String rawLine) {
        sb.append(rawLine).append('\n');
        return this;
    }

    /** @return true when no field has been added. */
    boolean isEmpty() {
        return sb.length() == 4; // only "---\n"
    }

    /**
     * Writes the frontmatter, the content, and a closing marker.
     * @param content the markdown body (may be blank).
     * @return the complete file text, ending in a newline.
     */
    String render(String content) {
        StringBuilder out = new StringBuilder(sb);
        out.append("---\n");
        if (content != null && !content.isBlank()) {
            out.append('\n').append(content.strip()).append('\n');
        }
        return out.toString();
    }

    /**
     * Single-quotes a scalar, doubling any internal single quote.
     * @param value the raw value.
     * @return a YAML-safe scalar.
     */
    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
