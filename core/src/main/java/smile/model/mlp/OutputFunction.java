/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.model.mlp;

import smile.math.MathEx;
import smile.tensor.Vector;

/**
 * The output function of neural networks.
 *
 * @author Haifeng Li
 */
public enum OutputFunction {
    /**
     * Linear/Identity function.
     */
    LINEAR {
        @Override
        public void f(Vector x) {
            // Identity function keeps the input as is.
        }

        @Override
        public void g(Cost cost, Vector g, Vector y) {
            switch (cost) {
                case MEAN_SQUARED_ERROR:
                    // no-op as the gradient is just o - t
                    break;

                case LIKELIHOOD:
                    throw new IllegalStateException("SOFTMAX must work with LIKELIHOOD cost function");
            }
        }
    },

    /**
     * Logistic sigmoid function: sigmoid(v)=1/(1+exp(-v)).
     * For multi-class classification, each unit in output layer
     * corresponds to a class. For binary classification and cross
     * entropy error function, there is only one output unit whose
     * value can be regarded as posteriori probability.
     */
    SIGMOID {
        @Override
        public void f(Vector x) {
            for (int i = 0; i < x.size(); i++) {
                x.set(i, MathEx.sigmoid(x.get(i)));
            }
        }

        @Override
        public void g(Cost cost, Vector g, Vector y) {
            switch (cost) {
                case MEAN_SQUARED_ERROR:
                    for (int i = 0; i < g.size(); i++) {
                        double yi = y.get(i);
                        g.mul(i, yi * (1.0 - yi));
                    }
                    break;

                case LIKELIHOOD:
                    // no-op as the gradient is just o - t
                    break;
            }
        }
    },

    /**
     * Softmax for multi-class cross entropy objection function.
     * The values of units in output layer can be regarded as posteriori
     * probabilities of each class.
     */
    SOFTMAX {
        @Override
        public void f(Vector x) {
            x.softmax();
        }

        @Override
        public void g(Cost cost, Vector g, Vector y) {
            switch (cost) {
                case MEAN_SQUARED_ERROR:
                    throw new IllegalStateException("SOFTMAX must work with LIKELIHOOD cost function");

                case LIKELIHOOD:
                    // no-op as the gradient is just o - t
                    break;
            }
        }
    };

    /**
     * The output function.
     * @param x the input vector.
     */
    public abstract void f(Vector x);

    /**
     * The gradient function.
     * @param cost the cost function of neural network.
     * @param g the gradient vector. On input, it holds target - output.
     *          On output, it is the gradient.
     * @param y the output vector.
     */
    public abstract void g(Cost cost, Vector g, Vector y);
}
