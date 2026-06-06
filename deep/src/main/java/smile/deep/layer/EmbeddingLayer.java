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
package smile.deep.layer;

import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Tensor;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * An embedding layer that is a simple lookup table that stores embeddings
 * of a fixed dictionary and size.
 * <p>
 * This layer is often used to store word embeddings and retrieve them using
 * indices. The input to the module is a list of indices, and the output is
 * the corresponding word embeddings.
 *
 * @author Haifeng Li
 */
public class EmbeddingLayer extends AbstractLayer {
    /** The optional scaling factor. */
    private final double alpha;

    /**
     * Constructor.
     * @param numTokens the size of the dictionary of embeddings.
     * @param dim the size of each embedding vector.
     */
    public EmbeddingLayer(int numTokens, int dim) {
        this(numTokens, dim, 1.0);
    }

    /**
     * Constructor.
     * @param numTokens the size of the dictionary of embeddings.
     * @param dim the size of each embedding vector.
     * @param alpha optional scaling factor.
     */
    public EmbeddingLayer(int numTokens, int dim, double alpha) {
        super(create(numTokens, dim));
        this.alpha = alpha;
    }

    private static Handles create(int numTokens, int dim) {
        MemorySegment h = check(smile_embedding_create(numTokens, dim));
        MemorySegment m = check(smile_embedding_as_module(h));
        return new Handles(h, m, () -> {
            smile_module_free(m);
            smile_embedding_free(h);
        });
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor x = new Tensor(smile_embedding_forward(handle, input.handle()));
        return alpha != 1.0 ? x.mul_(alpha) : x;
    }
}
