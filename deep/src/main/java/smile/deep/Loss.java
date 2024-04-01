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
        return (Tensor input, Tensor target) -> Tensor.of(torch.l1_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Mean Squared Error (L2) Loss Function.
     * @return the loss functor.
     */
    static Loss mse() {
        return (Tensor input, Tensor target) -> Tensor.of(torch.mse_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Negative Log-Likelihood Loss Function.
     * @return the loss functor.
     */
    static Loss nll() {
        return (Tensor input, Tensor target) -> Tensor.of(torch.nll_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Cross Entropy Loss Function.
     * @return the loss functor.
     */
    static Loss crossEntropy() {
        return (Tensor input, Tensor target) -> Tensor.of(torch.cross_entropy_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Hinge Embedding Loss Function.
     * @return the loss functor.
     */
    static Loss hingeEmbedding() {
        return (Tensor input, Tensor target) -> Tensor.of(torch.hinge_embedding_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Kullback-Leibler Divergence Loss Function.
     * @return the loss functor.
     */
    static Loss kl() {
        return (Tensor input, Tensor target) -> Tensor.of(torch.kl_div(input.asTorch(), target.asTorch()));
    }

    /**
     * Margin Ranking Loss Function.
     * @param input1 the first input.
     * @param input2 the second input.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor marginRanking(Tensor input1, Tensor input2, Tensor target) {
        return Tensor.of(torch.margin_ranking_loss(input1.asTorch(), input2.asTorch(), target.asTorch()));
    }

    /**
     * Triplet Margin Ranking Loss Function.
     * @param anchor the first input.
     * @param positive the second input.
     * @param negative the third input.
     * @return the loss.
     */
    static Tensor tripleMarginRanking(Tensor anchor, Tensor positive, Tensor negative) {
        return Tensor.of(torch.triplet_margin_loss(anchor.asTorch(), positive.asTorch(), negative.asTorch()));
    }
}
