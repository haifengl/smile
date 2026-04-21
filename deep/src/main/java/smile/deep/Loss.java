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
import org.bytedeco.pytorch.global.torch;

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
        return (input, target) -> new Tensor(torch.l1_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Mean Squared Error (L2) Loss Function.
     * @return the loss functor.
     */
    static Loss mse() {
        return (input, target) -> new Tensor(torch.mse_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Negative Log-Likelihood Loss Function.
     * @return the loss functor.
     */
    static Loss nll() {
        return (input, target) -> new Tensor(torch.nll_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Cross Entropy Loss Function.
     * @return the loss functor.
     */
    static Loss crossEntropy() {
        return (input, target) -> new Tensor(torch.cross_entropy_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Hinge Embedding Loss Function.
     * @return the loss functor.
     */
    static Loss hingeEmbedding() {
        return (input, target) -> new Tensor(torch.hinge_embedding_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Binary Cross-Entropy Loss Function. Measures the binary cross-entropy
     * between the target and the input probabilities. Input should be in [0,1].
     * @return the loss functor.
     */
    static Loss bce() {
        return (input, target) -> new Tensor(torch.binary_cross_entropy(input.asTorch(), target.asTorch()));
    }

    /**
     * Binary Cross-Entropy with Logits Loss Function. Combines a sigmoid
     * activation and binary cross-entropy in a numerically stable way.
     * @return the loss functor.
     */
    static Loss bceWithLogits() {
        return (input, target) -> new Tensor(torch.binary_cross_entropy_with_logits(input.asTorch(), target.asTorch()));
    }

    /**
     * Smooth L1 (Huber) Loss Function. Uses a squared term if the absolute
     * element-wise error falls below beta (default 1) and an L1 term otherwise.
     * This is less sensitive to outliers than MSE and avoids the gradient
     * discontinuity of plain MAE.
     * @return the loss functor.
     */
    static Loss smoothL1() {
        return (input, target) -> new Tensor(torch.smooth_l1_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Huber Loss Function. Equivalent to smooth L1 when delta = 1.
     * @param delta the threshold at which to change between L1 and L2.
     * @return the loss functor.
     */
    static Loss huber(double delta) {
        return (input, target) -> {
            var options = new org.bytedeco.pytorch.HuberLossOptions();
            options.delta().put(delta);
            return new Tensor(torch.huber_loss(input.asTorch(), target.asTorch(), options));
        };
    }

    /**
     * Kullback-Leibler Divergence Loss Function.
     * @return the loss functor.
     */
    static Loss kl() {
        return (input, target) -> new Tensor(torch.kl_div(input.asTorch(), target.asTorch()));
    }

    /**
     * Margin Ranking Loss Function.
     * @param input1 the first input.
     * @param input2 the second input.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor marginRanking(Tensor input1, Tensor input2, Tensor target) {
        return new Tensor(torch.margin_ranking_loss(input1.asTorch(), input2.asTorch(), target.asTorch()));
    }

    /**
     * Triplet Margin Ranking Loss Function.
     * @param anchor the first input.
     * @param positive the second input.
     * @param negative the third input.
     * @return the loss.
     */
    static Tensor tripleMarginRanking(Tensor anchor, Tensor positive, Tensor negative) {
        return new Tensor(torch.triplet_margin_loss(anchor.asTorch(), positive.asTorch(), negative.asTorch()));
    }
}
