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
package smile.llm.attention;

/**
 * Pluggable attention kernel backends (SGLang-style names).
 *
 * @author Haifeng Li
 */
public enum AttentionBackend {
    /** LibTorch {@code scaled_dot_product_attention} (contiguous Q/K/V). */
    TORCH_NATIVE("torch_native"),
    /** FlashInfer paged BatchPrefill / BatchDecode. */
    FLASHINFER("flashinfer");

    private final String id;

    AttentionBackend(String id) {
        this.id = id;
    }

    /**
     * Config / CLI identifier (e.g. {@code torch_native}).
     * @return lowercase backend id.
     */
    public String id() {
        return id;
    }

    /**
     * Parses a config string (case-insensitive).
     *
     * @param value {@code torch_native} or {@code flashinfer}.
     * @return matching backend.
     * @throws IllegalArgumentException if {@code value} is unknown.
     */
    public static AttentionBackend parse(String value) {
        if (value == null || value.isBlank()) {
            return TORCH_NATIVE;
        }
        String v = value.trim().toLowerCase();
        for (AttentionBackend b : values()) {
            if (b.id.equals(v) || b.name().equalsIgnoreCase(v)) {
                return b;
            }
        }
        throw new IllegalArgumentException(
                "Unknown attention backend '" + value + "'; expected torch_native|flashinfer");
    }

    @Override
    public String toString() {
        return id;
    }
}
