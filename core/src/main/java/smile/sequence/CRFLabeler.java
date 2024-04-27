/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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

package smile.sequence;

import java.io.Serial;
import java.util.Arrays;
import java.util.Properties;
import java.util.function.Function;
import smile.data.Tuple;

/**
 * First-order CRF sequence labeler.
 *
 * @param <T> the data type of model input objects.
 *
 * @author Haifeng Li
 */
public class CRFLabeler<T> implements SequenceLabeler<T> {
    @Serial
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
     * Fits a CRF model.
     * @param sequences the training data.
     * @param labels the training sequence labels.
     * @param features the feature function.
     * @param <T> the data type of observations.
     * @return the model.
     */
    public static <T> CRFLabeler<T> fit(T[][] sequences, int[][] labels, Function<T, Tuple> features) {
        return fit(sequences, labels, features, new Properties());
    }

    /**
     * Fits a CRF model.
     * @param sequences the training data.
     * @param labels the training sequence labels.
     * @param features the feature function.
     * @param params the hyperparameters.
     * @param <T> the data type of observations.
     * @return the model.
     */
    public static <T> CRFLabeler<T> fit(T[][] sequences, int[][] labels, Function<T, Tuple> features, Properties params) {
        int ntrees = Integer.parseInt(params.getProperty("smile.crf.trees", "100"));
        int maxDepth = Integer.parseInt(params.getProperty("smile.crf.max_depth", "20"));
        int maxNodes = Integer.parseInt(params.getProperty("smile.crf.max_nodes", "100"));
        int nodeSize = Integer.parseInt(params.getProperty("smile.crf.node_size", "5"));
        double shrinkage = Double.parseDouble(params.getProperty("smile.crf.shrinkage", "1.0"));
        return fit(sequences, labels, features, ntrees, maxDepth, maxNodes, nodeSize, shrinkage);
    }

    /**
     * Fits a CRF.
     *
     * @param sequences the observation sequences.
     * @param labels the state labels of observations, of which states take
     *               values in [0, k), where k is the number of hidden states.
     * @param features the feature function.
     * @param ntrees the number of trees/iterations.
     * @param maxDepth the maximum depth of the tree.
     * @param maxNodes the maximum number of leaf nodes in the tree.
     * @param nodeSize  the number of instances in a node below which the tree will
     *                  not split, setting nodeSize = 5 generally gives good results.
     * @param shrinkage the shrinkage parameter in (0, 1] controls the learning rate of procedure.
     * @param <T> the data type of observations.
     * @return the model.
     */
    public static <T> CRFLabeler<T> fit(T[][] sequences, int[][] labels, Function<T, Tuple> features, int ntrees, int maxDepth, int maxNodes, int nodeSize, double shrinkage) {
        if (sequences.length != labels.length) {
            throw new IllegalArgumentException("The number of observation sequences and that of label sequences are different.");
        }

        CRF model = CRF.fit(
                Arrays.stream(sequences)
                        .map(sequence -> Arrays.stream(sequence).map(features).toArray(Tuple[]::new))
                        .toArray(Tuple[][]::new),
                labels, ntrees, maxDepth, maxNodes, nodeSize, shrinkage);

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
        return Arrays.stream(o).map(features).toArray(Tuple[]::new);
    }

    /**
     * Returns the most likely label sequence given the feature sequence by the
     * forward-backward algorithm.
     *
     * @param o the observation sequence.
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
     *
     * @param o the observation sequence.
     * @return the sequence labels.
     */
    public int[] viterbi(T[] o) {
        return model.viterbi(translate(o));
    }
}
