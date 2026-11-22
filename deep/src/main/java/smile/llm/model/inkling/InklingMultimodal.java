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

import smile.deep.tensor.Tensor;
import smile.llm.engine.MultimodalPrompt;

/**
 * An Inkling-2 prompt with its images preprocessed: the token ids (every image placeholder expanded to one token per
 * 40x40 tile) and the tiles the vision tower encodes at the start of prefill.
 *
 * @param inputIds expanded prompt token ids.
 * @param pixels   tiles of all images back to back {@code [N, 40, 40, 3]} float32 on the CPU, or {@code null}.
 * @param grids    one {@code (1, rows, cols)} tile grid per image, in prompt order.
 *
 * @author Haifeng Li
 */
public record InklingMultimodal(int[] inputIds, Tensor pixels, int[][] grids) implements MultimodalPrompt {
    @Override
    public boolean hasVision() {
        return pixels != null;
    }

    /** Releases the CPU tiles. */
    public void close() {
        if (pixels != null) {
            pixels.close();
        }
    }
}
