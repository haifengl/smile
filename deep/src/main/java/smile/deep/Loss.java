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

import smile.deep.tensor.Tensor;
import static org.bytedeco.pytorch.global.torch.*;

/**
 * Loss functions.
 *
 * @author Haifeng Li
 */
public interface Loss {
    /**
     * Mean Absolute Error (L1) Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor l1(Tensor input, Tensor target) {
        return Tensor.of(l1_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Mean Squared Error (L2) Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor mse(Tensor input, Tensor target) {
        return Tensor.of(mse_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Negative Log-Likelihood Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor nll(Tensor input, Tensor target) {
        return Tensor.of(nll_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Cross Entropy Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor crossEntropy(Tensor input, Tensor target) {
        return Tensor.of(cross_entropy_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Hinge Embedding Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor hingeEmbedding(Tensor input, Tensor target) {
        return Tensor.of(hinge_embedding_loss(input.asTorch(), target.asTorch()));
    }

    /**
     * Margin Ranking Loss Function.
     * @param input1 the first input.
     * @param input2 the second input.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor marginRanking(Tensor input1, Tensor input2, Tensor target) {
        return Tensor.of(margin_ranking_loss(input1.asTorch(), input2.asTorch(), target.asTorch()));
    }

    /**
     * Triplet Margin Ranking Loss Function.
     * @param anchor the first input.
     * @param positive the second input.
     * @param negative the third input.
     * @return the loss.
     */
    static Tensor tripleMarginRanking(Tensor anchor, Tensor positive, Tensor negative) {
        return Tensor.of(triplet_margin_loss(anchor.asTorch(), positive.asTorch(), negative.asTorch()));
    }

    /**
     * Kullback-Leibler Divergence Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor kl(Tensor input, Tensor target) {
        return Tensor.of(kl_div(input.asTorch(), target.asTorch()));
    }
}
