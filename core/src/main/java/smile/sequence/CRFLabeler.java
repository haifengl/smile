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
import java.util.function.Function;
import smile.data.Tuple;
import smile.math.MathEx;

/**
 * First-order CRF sequence labeler.
 *
 * @author Haifeng Li
 */
public class CRFLabeler<T> implements SequenceLabeler<T> {
    private static final long serialVersionUID = 2L;

    /** The CRF model. */
    public final CRF model;
    /** The feature function. */
    public final Function<T, Tuple> features;

    /**
     * Constructor.
     *
     * @param model the CRF model.
     * @param features the feature function.
     */
    public CRFLabeler(CRF model, Function<T, Tuple> features) {
        this.model = model;
        this.features = features;
    }

    /**
     * Fits a CRF.
     *
     * @param observations the observation sequences.
     * @param labels the state labels of observations, of which states take
     *               values in [0, k), where k is the number of hidden states.
     * @param features the feature function.
     */
    public static <T> CRFLabeler<T> fit(T[][] observations, int[][] labels, Function<T, Tuple> features) {
        if (observations.length != labels.length) {
            throw new IllegalArgumentException("The number of observation sequences and that of label sequences are different.");
        }

        CRF model = CRF.fit(
                Arrays.stream(observations)
                        .map(sequence -> Arrays.stream(sequence).map(symbol -> features.apply(symbol)).toArray(Tuple[]::new))
                        .toArray(Tuple[][]::new),
                labels,
                MathEx.max(labels) + 1);

        return new CRFLabeler<>(model, features);
    }

    @Override
    public String toString() {
        return model.toString();
    }

    /**
     * Translates an observation sequence to internal representation.
     */
    private Tuple[] translate(T[] o) {
        return Arrays.stream(o).map(symbol -> features.apply(symbol)).toArray(Tuple[]::new);
    }

    /**
     * Returns the most likely label sequence given the feature sequence by the
     * forward-backward algorithm.
     *
     * @param o an observation sequence.
     * @return the most likely state sequence.
     */
    @Override
    public int[] predict(T[] o) {
        return model.predict(translate(o));
    }

    /**
     * Labels sequence with Viterbi algorithm. Viterbi algorithm
     * returns the whole sequence label that has the maximum probability,
     * which makes sense in applications (e.g.part-of-speech tagging) that
     * require coherent sequential labeling. The forward-backward algorithm
     * labels a sequence by individual prediction on each position.
     * This usually produces better accuracy although the results may not
     * be coherent.
     */
    public int[] viterbi(T[] o) {
        return model.viterbi(translate(o));
    }
}
