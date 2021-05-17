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

import java.io.IOException;

/**
 * An input layer in the neural network.
 *
 * @author Haifeng Li
 */
public class InputLayer extends Layer {
    private static final long serialVersionUID = 2L;

    /**
     * Constructor.
     * @param p the number of input variables (not including bias value).
     */
    public InputLayer(int p) {
        this(p, 0.0);
    }

    /**
     * Constructor.
     * @param p the number of input variables (not including bias value).
     * @param dropout the dropout rate.
     */
    public InputLayer(int p, double dropout) {
        super(p, dropout);
    }

    /**
     * Initializes the workspace when deserializing the object.
     * @param in the input stream.
     * @throws IOException when fails to read the stream.
     * @throws ClassNotFoundException when fails to load the class.
     */
    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        output = ThreadLocal.withInitial(() -> new double[n]);

        if (dropout > 0.0) {
            mask = ThreadLocal.withInitial(() -> new byte[n]);
        }
    }

    @Override
    public String toString() {
        if (dropout > 0.0) {
            return String.format("Input(%d, %.2f)", n, dropout);
        } else {
            return String.format("Input(%d)", n);
        }
    }

    @Override
    public void propagate(double[] x) {
        System.arraycopy(x, 0, output.get(), 0, p);
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