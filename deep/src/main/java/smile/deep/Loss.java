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

import java.util.function.BiFunction;
import smile.deep.tensor.Tensor;

import static smile.torch.smile_torch_h.*;

/**
 * Loss functions.
 *
 * @author Haifeng Li
 */
public interface Loss extends BiFunction<Tensor, Tensor, Tensor> {
    /**
     * Mean Absolute Error (L1) Loss Function.
     * @return the loss functor.
     */
    static Loss l1() {
        return (input, target) -> new Tensor(smile_torch_l1_loss(input.handle(), target.handle()));
    }

    /**
     * Mean Squared Error (L2) Loss Function.
     * @return the loss functor.
     */
    static Loss mse() {
        return (input, target) -> new Tensor(smile_torch_mse_loss(input.handle(), target.handle()));
    }

    /**
     * Negative Log-Likelihood Loss Function.
     * @return the loss functor.
     */
    static Loss nll() {
        return (input, target) -> new Tensor(smile_torch_nll_loss(input.handle(), target.handle()));
    }

    /**
     * Cross Entropy Loss Function.
     * @return the loss functor.
     */
    static Loss crossEntropy() {
        // ignore_index = -100, reduction = mean (ST_REDUCTION_MEAN = 1) — PyTorch defaults.
        return (input, target) -> new Tensor(smile_torch_cross_entropy(input.handle(), target.handle(), -100L, ST_REDUCTION_MEAN()));
    }

    /**
     * Hinge Embedding Loss Function.
     * @return the loss functor.
     */
    static Loss hingeEmbedding() {
        return (input, target) -> new Tensor(smile_torch_hinge_embedding_loss(input.handle(), target.handle()));
    }

    /**
     * Binary Cross-Entropy Loss Function. Measures the binary cross-entropy
     * between the target and the input probabilities. Input should be in [0,1].
     * @return the loss functor.
     */
    static Loss bce() {
        return (input, target) -> new Tensor(smile_torch_binary_cross_entropy(input.handle(), target.handle()));
    }

    /**
     * Binary Cross-Entropy with Logits Loss Function. Combines a sigmoid
     * activation and binary cross-entropy in a numerically stable way.
     * @return the loss functor.
     */
    static Loss bceWithLogits() {
        return (input, target) -> new Tensor(smile_torch_binary_cross_entropy_logits(input.handle(), target.handle()));
    }

    /**
     * Smooth L1 (Huber) Loss Function. Uses a squared term if the absolute
     * element-wise error falls below beta (default 1) and an L1 term otherwise.
     * This is less sensitive to outliers than MSE and avoids the gradient
     * discontinuity of plain MAE.
     * @return the loss functor.
     */
    static Loss smoothL1() {
        return (input, target) -> new Tensor(smile_torch_smooth_l1_loss(input.handle(), target.handle()));
    }

    /**
     * Huber Loss Function. Equivalent to smooth L1 when delta = 1.
     * @param delta the threshold at which to change between L1 and L2.
     * @return the loss functor.
     */
    static Loss huber(double delta) {
        return (input, target) -> new Tensor(smile_torch_huber_loss(input.handle(), target.handle(), delta));
    }

    /**
     * Kullback-Leibler Divergence Loss Function.
     * @return the loss functor.
     */
    static Loss kl() {
        return (input, target) -> new Tensor(smile_torch_kl_div(input.handle(), target.handle()));
    }

    /**
     * Margin Ranking Loss Function.
     * @param input1 the first input.
     * @param input2 the second input.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor marginRanking(Tensor input1, Tensor input2, Tensor target) {
        return new Tensor(smile_torch_margin_ranking_loss(input1.handle(), input2.handle(), target.handle()));
    }

    /**
     * Triplet Margin Ranking Loss Function.
     * @param anchor the first input.
     * @param positive the second input.
     * @param negative the third input.
     * @return the loss.
     */
    static Tensor tripleMarginRanking(Tensor anchor, Tensor positive, Tensor negative) {
        return new Tensor(smile_torch_triplet_margin_loss(anchor.handle(), positive.handle(), negative.handle()));
    }
}
