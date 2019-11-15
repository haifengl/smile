/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
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
 *******************************************************************************/

package smile.sequence;

import java.util.Arrays;
import java.util.function.ToIntFunction;

/**
 * First-order Hidden Markov Model sequence labeler.
 *
 * @author Haifeng Li
 */
public class HMMLabeler<T> implements SequenceLabeler<T> {
    private static final long serialVersionUID = 2L;

    /** The HMM model. */
    public final HMM model;
    /** The lambda returns the ordinal numbers of symbols. */
    public final ToIntFunction<T> ordinal;

    /**
     * Constructor.
     *
     * @param model the HMM model.
     * @param ordinal a lambda returning the ordinal numbers of symbols.
     */
    public HMMLabeler(HMM model, ToIntFunction<T> ordinal) {
        this.model = model;
        this.ordinal = ordinal;
    }

    /**
     * Fits an HMM by maximum likelihood estimation.
     *
     * @param observations the observation sequences.
     * @param labels the state labels of observations, of which states take
     *               values in [0, p), where p is the number of hidden states.
     * @param ordinal a lambda returning the ordinal numbers of symbols.
     */
    public static <T> HMMLabeler<T> fit(T[][] observations, int[][] labels, ToIntFunction<T> ordinal) {
        if (observations.length != labels.length) {
            throw new IllegalArgumentException("The number of observation sequences and that of label sequences are different.");
        }

        HMM model = HMM.fit(
                Arrays.stream(observations)
                        .map(sequence -> Arrays.stream(sequence).mapToInt(symbol -> ordinal.applyAsInt(symbol)).toArray())
                        .toArray(int[][]::new),
                labels);

        return new HMMLabeler<>(model, ordinal);
    }

    /**
     * Updates the HMM by the Baum-Welch algorithm.
     *
     * @param observations the training observation sequences.
     * @param iterations the number of iterations to execute.
     */
    public void update(T[][] observations, int iterations) {
        model.update(
                Arrays.stream(observations)
                        .map(sequence -> Arrays.stream(sequence).mapToInt(symbol -> ordinal.applyAsInt(symbol)).toArray())
                        .toArray(int[][]::new),
                iterations);
    }

    @Override
    public String toString() {
        return model.toString();
    }

    /**
     * Translates an observation sequence to internal representation.
     */
    private int[] translate(T[] o) {
        return Arrays.stream(o).mapToInt(symbol -> ordinal.applyAsInt(symbol)).toArray();
    }

    /**
     * Returns the joint probability of an observation sequence along a state
     * sequence.
     *
     * @param o an observation sequence.
     * @param s a state sequence.
     * @return the joint probability P(o, s | H) given the model H.
     */
    public double p(T[] o, int[] s) {
        return model.p(translate(o), s);
    }

    /**
     * Returns the log joint probability of an observation sequence along a
     * state sequence.
     *
     * @param o an observation sequence.
     * @param s a state sequence.
     * @return the log joint probability P(o, s | H) given the model H.
     */
    public double logp(T[] o, int[] s) {
        return model.logp(translate(o), s);
    }

    /**
     * Returns the probability of an observation sequence.
     *
     * @param o an observation sequence.
     * @return the probability of this sequence.
     */
    public double p(T[] o) {
        return model.p(translate(o));
    }

    /**
     * Returns the logarithm probability of an observation sequence.
     * A scaling procedure is used in order to avoid underflows when
     * computing the probability of long sequences.
     *
     * @param o an observation sequence.
     * @return the log probability of this sequence.
     */
    public double logp(T[] o) {
        return model.logp(translate(o));
    }

    /**
     * Returns the most likely state sequence given the observation sequence by
     * the Viterbi algorithm, which maximizes the probability of
     * <code>P(I | O, HMM)</code>. In the calculation, we may get ties. In this
     * case, one of them is chosen randomly.
     *
     * @param o an observation sequence.
     * @return the most likely state sequence.
     */
    @Override
    public int[] predict(T[] o) {
        return model.predict(translate(o));
    }
}
