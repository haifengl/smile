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
package smile.llm.model.inkling;

import java.util.function.BiConsumer;
import smile.deep.tensor.Tensor;

/**
 * Debug taps on the layer stages of the reference and the fused decode paths (tests compare them stage by stage).
 *
 * @author Haifeng Li
 */
final class InklingDebug {
    /** Receives {@code (name, tensor)} with names such as {@code attn.11}; {@code null} when off. */
    static volatile BiConsumer<String, Tensor> hook;

    private InklingDebug() { }

    static void tap(String stage, int app, Tensor t) {
        BiConsumer<String, Tensor> h = hook;
        if (h != null) {
            h.accept(stage + "." + app, t);
        }
    }
}
