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
package smile.llm.model.minimax;

import smile.deep.tensor.Tensor;
import smile.llm.engine.MultimodalPrompt;

/**
 * A MiniMax-M3 prompt with its images and videos preprocessed: the token ids (every media placeholder expanded to
 * one id per vision token) plus the patch matrices and grids the vision tower encodes during prefill.
 *
 * @param inputIds    expanded prompt token ids.
 * @param imagePixels patches of all images back to back, {@code [N, patchDim]} float32 on the CPU, or {@code null}.
 * @param imageGrids  one {@code (t, h, w)} patch grid per image, in prompt order.
 * @param videoPixels patches of all videos back to back, or {@code null}.
 * @param videoGrids  one {@code (t, h, w)} patch grid per video, in prompt order.
 *
 * @author Haifeng Li
 */
public record MiniMaxMultimodal(int[] inputIds, Tensor imagePixels, int[][] imageGrids,
                                Tensor videoPixels, int[][] videoGrids) implements MultimodalPrompt {
    @Override
    public boolean hasVision() {
        return imagePixels != null || videoPixels != null;
    }

    /** Releases the CPU patch matrices. */
    public void close() {
        if (imagePixels != null) {
            imagePixels.close();
        }
        if (videoPixels != null) {
            videoPixels.close();
        }
    }
}
