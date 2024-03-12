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
package smile.deep;

import org.bytedeco.pytorch.Adam;
import org.bytedeco.pytorch.AdamOptions;
import org.bytedeco.pytorch.SGD;
import org.bytedeco.pytorch.SGDOptions;

/**
 * Optimizer functions.
 *
 * @author Haifeng Li
 */
public class Optimizer {
    org.bytedeco.pytorch.Optimizer optimizer;

    /** Constructor. */
    Optimizer(org.bytedeco.pytorch.Optimizer optimizer) {
        this.optimizer = optimizer;
    }

    /** Resets gradients. */
    public void reset() {
        optimizer.zero_grad();
    }

    /** Updates the parameters based on the calculated gradients. */
    public void step() {
        optimizer.step();
    }

    /**
     * Returns a stochastic gradient descent optimizer without momentum.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @return the optimizer.
     */
    public static Optimizer sgd(Model model, double rate) {
        return sgd(model, rate, 0.0, 0.0, 0.0, false);
    }

    /**
     * Returns a stochastic gradient descent optimizer with momentum.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @param momentum the momentum factor.
     * @param decay the weight decay (L2 penalty).
     * @param dampening dampening for momentum.
     * @param nesterov enables Nesterov momentum.
     * @return the optimizer.
     */
    public static Optimizer sgd(Model model, double rate, double momentum, double decay, double dampening, boolean nesterov) {
        SGDOptions options = new SGDOptions(rate);
        options.momentum().put(momentum);
        options.weight_decay().put(decay);
        options.dampening().put(dampening);
        options.nesterov().put(nesterov);
        return new Optimizer(new SGD(model.net.parameters(), options));
    }

    /**
     * Returns an Adam optimizer.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @return the optimizer.
     */
    public static Optimizer adam(Model model, double rate) {
        return adam(model, rate, 0.9, 0.999, 1E-08, 0, false);
    }

    /**
     * Returns an Adam optimizer.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @param beta1 coefficients used for computing running averages of gradient and its square.
     * @param beta2 coefficients used for computing running averages of gradient and its square.
     * @param eps term added to the denominator to improve numerical stability.
     * @param decay the weight decay (L2 penalty).
     * @param amsgrad whether to use the AMSGrad variant of this algorithm from the paper On the Convergence of Adam and Beyond.
     * @return the optimizer.
     */
    public static Optimizer adam(Model model, double rate, double beta1, double beta2, double eps, double decay, boolean amsgrad) {
        AdamOptions options = new AdamOptions(rate);
        options.betas().put(beta1, beta2);
        options.eps().put(eps);
        options.weight_decay().put(decay);
        options.amsgrad().put(amsgrad);
        return new Optimizer(new Adam(model.net.parameters(), options));
    }
}
