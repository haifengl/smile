/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.base.mlp;

import smile.feature.FeatureTransform;

/**
 * An input layer in the neural network.
 *
 * @author Haifeng Li
 */
public class InputLayer extends Layer {
    private static final long serialVersionUID = 2L;

    /** The feature transformation such as standardization. */
    private final FeatureTransform transformer;

    /**
     * Constructor.
     * @param p the number of input variables (not including bias value).
     */
    public InputLayer(int p) {
        this(p, 0.0, null);
    }

    /**
     * Constructor.
     * @param p the number of input variables (not including bias value).
     * @param dropout the dropout rate.
     * @param transformer the optional input feature transformation.
     */
    public InputLayer(int p, double dropout, FeatureTransform transformer) {
        super(p, p, dropout);
        this.transformer = transformer;
    }

    @Override
    public String toString() {
        String s = String.format("Input(%d", p);
        if (dropoutRate > 0.0) {
            s = String.format("%s, %.2f", s, dropoutRate);
        }
        if (transformer != null) {
            s = String.format("%s, %s", s, transformer.getClass().getSimpleName());
        }
        return s + ")";
    }

    @Override
    public void propagate(double[] x) {
        if (transformer == null) {
            System.arraycopy(x, 0, output.get(), 0, p);
        } else {
            transformer.transform(x, output.get());
        }
    }

    @Override
    public void backpropagate(double[] lowerLayerGradient) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void transform(double[] x) {
        // identity activation function
    }

    @Override
    public void computeGradient(double[] x) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void computeGradientUpdate(double[] x, double learningRate, double momentum, double decay) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void update(int m, double learningRate, double momentum, double decay, double rho, double epsilon) {
        throw new UnsupportedOperationException();
    }
}