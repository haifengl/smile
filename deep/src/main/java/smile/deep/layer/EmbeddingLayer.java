/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.deep.layer;

import org.bytedeco.pytorch.EmbeddingImpl;
import org.bytedeco.pytorch.Scalar;
import smile.deep.tensor.Tensor;

/**
 * An embedding layer that is a simple lookup table that stores embeddings
 * of a fixed dictionary and size.
 *
 * This layer is often used to store word embeddings and retrieve them using
 * indices. The input to the module is a list of indices, and the output is
 * the corresponding word embeddings.
 *
 * @author Haifeng Li
 */
public class EmbeddingLayer implements Layer {
    /** The size of the dictionary of embeddings. */
    int numTokens;
    /** The size of each embedding vector. */
    int dim;
    /** The optional scaling factor. */
    double alpha;
    /** The wrapper of alpha. */
    Scalar scale;
    /** Implementation. */
    EmbeddingImpl module;

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
        this.numTokens = numTokens;
        this.dim = dim;
        this.alpha = alpha;
        this.scale = new Scalar(alpha);
        this.module = new EmbeddingImpl(numTokens, dim);
    }

    @Override
    public void register(String name, Layer parent) {
        this.module = parent.asTorch().register_module(name, new EmbeddingImpl(numTokens, dim));
    }

    @Override
    public Tensor forward(Tensor input) {
        org.bytedeco.pytorch.Tensor x = input.asTorch();
        x = module.forward(x);
        if (alpha != 1.0) {
            x.mul_(scale);
        }
        return Tensor.of(x);
    }

    @Override
    public EmbeddingImpl asTorch() {
        return module;
    }
}
