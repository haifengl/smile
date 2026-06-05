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
package smile.deep;

import java.lang.foreign.MemorySegment;
import smile.torch.Native;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * Optimizer functions.
 *
 * @author Haifeng Li
 */
public class Optimizer {
    /** The native {@code ST_Optimizer} handle. */
    final MemorySegment optimizer;

    /** Constructor. */
    Optimizer(MemorySegment optimizer) {
        this.optimizer = check(optimizer);
        MemorySegment opt = this.optimizer;
        Native.CLEANER.register(this, () -> smile_optimizer_free(opt));
    }

    /** Resets gradients. */
    public void reset() {
        smile_optimizer_zero_grad(optimizer);
    }

    /** Updates the parameters based on the calculated gradients. */
    public void step() {
        smile_optimizer_step(optimizer);
    }

    /**
     * Sets the learning rate.
     * @param rate the learning rate.
     */
    public void setLearningRate(double rate) {
        if (rate <= 0) throw new IllegalArgumentException("Learning rate must be positive: " + rate);
        smile_optimizer_set_lr(optimizer, rate);
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
        MemorySegment params = check(smile_module_parameters(model.asModule()));
        try {
            return new Optimizer(smile_sgd_create(params, rate, momentum, decay, dampening, nesterov ? 1 : 0));
        } finally {
            smile_tensor_vec_free(params);
        }
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
        MemorySegment params = check(smile_module_parameters(model.asModule()));
        try {
            return new Optimizer(smile_adam_create(params, rate, beta1, beta2, eps, decay, amsgrad ? 1 : 0));
        } finally {
            smile_tensor_vec_free(params);
        }
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
        MemorySegment params = check(smile_module_parameters(model.asModule()));
        try {
            return new Optimizer(smile_adamw_create(params, rate, beta1, beta2, eps, decay, amsgrad ? 1 : 0));
        } finally {
            smile_tensor_vec_free(params);
        }
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
        MemorySegment params = check(smile_module_parameters(model.asModule()));
        try {
            return new Optimizer(smile_rmsprop_create(params, rate, alpha, eps, decay, momentum, centered ? 1 : 0));
        } finally {
            smile_tensor_vec_free(params);
        }
    }
}
