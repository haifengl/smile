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

package smile.deep.activation;

/**
 * The rectifier activation function {@code max(0, x)}.
 * It is introduced with strong biological motivations and mathematical
 * justifications. The rectifier is the most popular activation function
 * for deep neural networks. A unit employing the rectifier is called a
 * rectified linear unit (ReLU).
 * <p>
 * ReLU neurons can sometimes be pushed into states in which they become
 * inactive for essentially all inputs. In this state, no gradients flow
 * backward through the neuron, and so the neuron becomes stuck in a
 * perpetually inactive state and "dies". This is a form of the vanishing
 * gradient problem. In some cases, large numbers of neurons in a network
 * can become stuck in dead states, effectively decreasing the model
 * capacity. This problem typically arises when the learning rate is
 * set too high. It may be mitigated by using leaky ReLUs instead,
 * which assign a small positive slope for {@code x < 0} however the
 * performance is reduced.
 *
 * @author Haifeng Li
 */
public class ReLU implements ActivationFunction {
    /** Default instance. */
    static ReLU instance = new ReLU();

    /**
     * Constructor.
     */
    public ReLU() {

    }

    @Override
    public String name() {
        return "ReLU";
    }

    @Override
    public void f(double[] x) {
        for (int i = 0; i < x.length; i++) {
            x[i] = Math.max(0.0, x[i]);
        }
    }

    @Override
    public void g(double[] g, double[] y) {
        for (int i = 0; i < g.length; i++) {
            g[i] *= y[i] > 0 ? 1 : 0;
        }
    }
}
