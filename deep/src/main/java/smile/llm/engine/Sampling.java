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
package smile.llm.engine;

import smile.deep.tensor.Tensor;

/**
 * Shared sampling helpers for greedy / temperature + nucleus decoding.
 *
 * @author Haifeng Li
 */
public final class Sampling {
    private Sampling() {}

    /**
     * Samples the next token from last-position logits {@code [batch, vocab]}
     * (or {@code [batch, 1, vocab]} squeezed by the caller).
     *
     * @param logits      last-step logits; not closed by this method.
     * @param temperature {@code <= 0} selects greedy {@code argmax}.
     * @param topp        nucleus threshold when {@code temperature > 0}.
     * @return owned token ids shaped {@code [batch]} (caller must close).
     */
    /**
     * Greedy argmax on device; synchronizes a single scalar to the host.
     *
     * @param logits last-step logits {@code [batch, vocab]} (or {@code [1, vocab]}).
     * @return sampled token id for batch row 0.
     */
    public static int sampleGreedyTokenId(Tensor logits) {
        try (Tensor arg = logits.argmax(-1, false);
             Tensor flat = arg.reshape(-1).contiguous()) {
            return (int) flat.longArray()[0];
        }
    }

    public static Tensor sampleNext(Tensor logits, double temperature, double topp) {
        if (temperature > 0) {
            try (var scaled = logits.div(temperature);
                 var probs = scaled.softmax(-1);
                 Tensor sampled = probs.topp(topp)) {
                return sampled.reshape(-1);
            }
        }
        try (Tensor arg = logits.argmax(-1, false)) {
            return arg.reshape(-1);
        }
    }

    /**
     * Merges sampled tokens with prompt-overlay positions using the input mask.
     *
     * @param textMask      {@code true} where the sequence still holds prompt tokens.
     * @param currentTokens tokens currently at this position (prompt or pad).
     * @param nextToken     freshly sampled tokens.
     * @return owned merged tokens (caller must close).
     */
    public static Tensor mergeWithPromptMask(Tensor textMask, Tensor currentTokens, Tensor nextToken) {
        return Tensor.where(textMask, currentTokens, nextToken);
    }
}
