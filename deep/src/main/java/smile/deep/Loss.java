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
        return new Tensor(l1_loss(input.value, target.value));
    }

    /**
     * Mean Squared Error (L2) Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor mse(Tensor input, Tensor target) {
        return new Tensor(mse_loss(input.value, target.value));
    }

    /**
     * Negative Log-Likelihood Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor nll(Tensor input, Tensor target) {
        return new Tensor(nll_loss(input.value, target.value));
    }

    /**
     * Cross Entropy Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor crossEntropy(Tensor input, Tensor target) {
        return new Tensor(cross_entropy_loss(input.value, target.value));
    }

    /**
     * Hinge Embedding Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor hingeEmbedding(Tensor input, Tensor target) {
        return new Tensor(hinge_embedding_loss(input.value, target.value));
    }

    /**
     * Margin Ranking Loss Function.
     * @param input1 the first input.
     * @param input2 the second input.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor marginRanking(Tensor input1, Tensor input2, Tensor target) {
        return new Tensor(margin_ranking_loss(input1.value, input2.value, target.value));
    }

    /**
     * Triplet Margin Ranking Loss Function.
     * @param anchor the first input.
     * @param positive the second input.
     * @param negative the third input.
     * @return the loss.
     */
    static Tensor tripleMarginRanking(Tensor anchor, Tensor positive, Tensor negative) {
        return new Tensor(triplet_margin_loss(anchor.value, positive.value, negative.value));
    }

    /**
     * Kullback-Leibler Divergence Loss Function.
     * @param input the input/prediction.
     * @param target the target/truth.
     * @return the loss.
     */
    static Tensor kl(Tensor input, Tensor target) {
        return new Tensor(kl_div(input.value, target.value));
    }
}
