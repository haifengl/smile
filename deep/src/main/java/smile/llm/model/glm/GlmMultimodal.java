/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.glm;

import smile.deep.tensor.Tensor;
import smile.llm.engine.MultimodalPrompt;

/**
 * A GLM-5.3-Flash prompt with its images preprocessed: the token ids (every image placeholder expanded to one id per
 * vision token) plus the patch rows and grids the vision tower encodes during prefill.
 *
 * @param inputIds    expanded prompt token ids.
 * @param imagePixels patch rows of all images back to back, {@code [N, C * P * P]} float32 on the CPU, or {@code null}.
 * @param imageGrids  one {@code (t, h, w)} patch grid per image, in prompt order.
 * @param imageKeys   one content key (SHA-256 of grid and patch rows) per image: the key of the encoded-image cache.
 *
 * @author Haifeng Li
 */
public record GlmMultimodal(int[] inputIds, Tensor imagePixels, int[][] imageGrids, String[] imageKeys) implements MultimodalPrompt {
    @Override
    public boolean hasVision() {
        return imagePixels != null;
    }

    /** Releases the CPU patch matrix. */
    public void close() {
        if (imagePixels != null) {
            imagePixels.close();
        }
    }
}
