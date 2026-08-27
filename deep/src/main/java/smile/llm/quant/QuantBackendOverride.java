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
package smile.llm.quant;

/**
 * Thread-local override for {@code smile.chat.quantization} during model load.
 *
 * @author Haifeng Li
 */
public final class QuantBackendOverride {
    private static final ThreadLocal<String> OVERRIDE = new ThreadLocal<>();

    private QuantBackendOverride() {}

    /** Sets the override for the current thread ({@code auto}/{@code dense}/…). */
    public static void set(String backend) {
        OVERRIDE.set(backend);
    }

    /** @return current override, or {@code null}. */
    public static String get() {
        return OVERRIDE.get();
    }

    /** Clears the override. */
    public static void clear() {
        OVERRIDE.remove();
    }
}
