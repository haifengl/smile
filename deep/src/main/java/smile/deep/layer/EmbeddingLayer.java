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

import org.bytedeco.pytorch.EmbeddingImpl;
import org.bytedeco.pytorch.Module;
import org.bytedeco.pytorch.Scalar;
import smile.deep.tensor.Tensor;

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
public class EmbeddingLayer implements Layer {
    /** The optional scaling factor. */
    private final double alpha;
    /** The wrapper of alpha. */
    private final Scalar scale;
    /** Implementation. */
    private final EmbeddingImpl module;

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
        this.alpha = alpha;
        this.scale = new Scalar(alpha);
        this.module = new EmbeddingImpl(numTokens, dim);
    }

    @Override
    public Module asTorch() {
        return module;
    }

    @Override
    public Tensor forward(Tensor input) {
        org.bytedeco.pytorch.Tensor x = input.asTorch();
        x = module.forward(x);
        if (alpha != 1.0) {
            x.mul_(scale);
        }
        return new Tensor(x);
    }
}
