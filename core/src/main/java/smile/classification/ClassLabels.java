/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.classification;

import java.io.Serializable;
import java.util.Arrays;
import java.util.stream.IntStream;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.vector.BaseVector;
import smile.math.MathEx;
import smile.util.IntSet;

/**
 * Map arbitrary class labels to [0, k), where k is the number of classes.
 *
 * @author Haifeng Li
 */
public class ClassLabels implements Serializable {
    private static final long serialVersionUID = 2L;

    /** The number of classes. */
    public final int k;
    /** The class labels. If the class labels are already in [0, k), this is empty. */
    public final IntSet labels;
    /** The sample class id in [0, k). */
    public final int[] y;
    /** The number of samples per classes. */
    public final int[] ni;
    /** The estimated priori probabilities. */
    public final double[] priori;

    /**
     * Constructor.
     * @param k The number of classes.
     * @param y The sample class id in [0, k).
     * @param labels the class label encoder.
     */
    public ClassLabels(int k, int[] y, IntSet labels) {
        this.k = k;
        this.y = y;
        this.labels = labels;
        this.ni = count(y, k);

        priori = new double[k];
        double n = y.length;
        for (int i = 0; i < k; i++) {
            priori[i] = ni[i] / n;
        }
    }

    /**
     * Returns the nominal scale for the class labels.
     * @return the nominal scale for the class labels.
     */
    public NominalScale scale() {
        String[] values = new String[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            values[i] = String.valueOf(labels.valueOf(i));
        }
        return new NominalScale(values);
    }

    /**
     * Maps the class labels to index.
     * @param y the sample labels.
     * @return the indices of labels.
     */
    public int[] indexOf(int[] y) {
        int[] x = new int[y.length];
        for (int i = 0; i < y.length; i++) {
            x[i] = labels.indexOf(y[i]);
        }
        return x;
    }

    /**
     * Learns the class label mapping from samples.
     * @param y the sample labels.
     * @return the class label mapping.
     */
    public static ClassLabels fit(int[] y) {
        int[] labels = MathEx.unique(y);
        Arrays.sort(labels);
        int k = labels.length;

        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");
        }

        IntSet encoder = new IntSet(labels);
        if (labels[0] == 0 && labels[k-1] == k-1) {
            return new ClassLabels(k, y, encoder);
        } else {
            return new ClassLabels(k, Arrays.stream(y).map(encoder::indexOf).toArray(), encoder);
        }
    }

    /**
     * Learns the class label mapping from samples.
     * @param response the sample labels.
     * @return the class label mapping.
     */
    public static ClassLabels fit(BaseVector response) {
        int[] y = response.toIntArray();

        Measure measure = response.measure();
        if (measure instanceof NominalScale) {
            NominalScale scale = (NominalScale) measure;
            int k = scale.size();
            int[] labels = IntStream.range(0, k).toArray();
            IntSet encoder = new IntSet(labels);
            return new ClassLabels(k, y, encoder);
        }

        return fit(y);
    }

    /**
     * Returns the number of samples per class.
     * @param y sample labels in [0, k)
     * @param k the number of classes.
     * @return the number of samples per class.
     */
    private static int[] count(int[] y, int k) {
        int[] ni = new int[k];

        for (int yi : y) {
            ni[yi]++;
        }

        return ni;
    }
}
