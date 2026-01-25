/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.deep;

import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.pytorch.*;

/**
 * Optimizer functions.
 *
 * @author Haifeng Li
 */
public class Optimizer {
    final org.bytedeco.pytorch.Optimizer optimizer;

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
     * Sets the learning rate.
     * @param rate the learning rate.
     */
    public void setLearningRate(double rate) {
        var groups = optimizer.param_groups();
        for (int i = 0; i < groups.size(); i++) {
            groups.get(i).options().set_lr(rate);
        }
    }

    /**
     * Returns a stochastic gradient descent optimizer without momentum.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @return the optimizer.
     */
    public static Optimizer SGD(Model model, double rate) {
        return SGD(model, rate, 0.0, 0.0, 0.0, false);
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
    public static Optimizer SGD(Model model, double rate, double momentum, double decay, double dampening, boolean nesterov) {
        SGDOptions options = new SGDOptions(rate);
        options.momentum().put(momentum);
        options.weight_decay().put(decay);
        options.dampening().put(dampening);
        options.nesterov().put(nesterov);
        return new Optimizer(new SGD(model.asTorch().parameters(), options));
    }

    /**
     * Returns an Adam optimizer.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @return the optimizer.
     */
    public static Optimizer Adam(Model model, double rate) {
        return Adam(model, rate, 0.9, 0.999, 1E-08, 0, false);
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
    public static Optimizer Adam(Model model, double rate, double beta1, double beta2, double eps, double decay, boolean amsgrad) {
        DoublePointer betas = new DoublePointer(beta1, beta2);
        AdamOptions options = new AdamOptions(rate);
        options.betas().put(betas);
        options.eps().put(eps);
        options.weight_decay().put(decay);
        options.amsgrad().put(amsgrad);
        betas.close();
        return new Optimizer(new Adam(model.asTorch().parameters(), options));
    }

    /**
     * Returns an AdamW optimizer.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @return the optimizer.
     */
    public static Optimizer AdamW(Model model, double rate) {
        return AdamW(model, rate, 0.9, 0.999, 1E-08, 0, false);
    }

    /**
     * Returns an AdamW optimizer.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @param beta1 coefficients used for computing running averages of gradient and its square.
     * @param beta2 coefficients used for computing running averages of gradient and its square.
     * @param eps term added to the denominator to improve numerical stability.
     * @param decay the weight decay (L2 penalty).
     * @param amsgrad whether to use the AMSGrad variant of this algorithm from the paper On the Convergence of Adam and Beyond.
     * @return the optimizer.
     */
    public static Optimizer AdamW(Model model, double rate, double beta1, double beta2, double eps, double decay, boolean amsgrad) {
        AdamWOptions options = new AdamWOptions(rate);
        options.betas().put(beta1, beta2);
        options.eps().put(eps);
        options.weight_decay().put(decay);
        options.amsgrad().put(amsgrad);
        return new Optimizer(new AdamW(model.asTorch().parameters(), options));
    }

    /**
     * Returns an RMSprop optimizer.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @return the optimizer.
     */
    public static Optimizer RMSprop(Model model, double rate) {
        return RMSprop(model, rate, 0.99, 1E-08, 0, 0, false);
    }

    /**
     * Returns an RMSprop optimizer.
     * @param model the model to be optimized.
     * @param rate the learning rate.
     * @param alpha smoothing constant.
     * @param eps term added to the denominator to improve numerical stability.
     * @param decay the weight decay (L2 penalty).
     * @param momentum the momentum factor.
     * @param centered if true, compute the centered RMSProp, the gradient is normalized by an estimation of its variance.
     * @return the optimizer.
     */
    public static Optimizer RMSprop(Model model, double rate, double alpha, double eps, double decay, double momentum, boolean centered) {
        RMSpropOptions options = new RMSpropOptions(rate);
        options.alpha().put(alpha);
        options.eps().put(eps);
        options.momentum().put(momentum);
        options.weight_decay().put(decay);
        options.centered().put(centered);
        return new Optimizer(new RMSprop(model.asTorch().parameters(), options));
    }
}
