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

package smile.classification;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;
import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;
import smile.data.vector.BaseVector;
import smile.math.MathEx;
import smile.util.IntSet;

/**
 * To support arbitrary class labels.
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
    /** The optional meta data of response variable. */
    public final StructField field;

    /** Constructor. */
    public ClassLabels(int k, int[] y, IntSet labels) {
        this(k, y, labels, null);
    }

    /** Constructor. */
    public ClassLabels(int k, int[] y, IntSet labels, StructField field) {
        this.k = k;
        this.y = y;
        this.labels = labels;
        this.field = field;
        this.ni = count(y, k);

        priori = new double[k];
        double n = y.length;
        for (int i = 0; i < k; i++) {
            priori[i] = ni[i] / n;
        }
    }

    /** Returns the nominal scale for the class labels. */
    public NominalScale scale() {
        String[] values = new String[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            values[i] = String.valueOf(labels.valueOf(i));
        }
        return new NominalScale(values);
    }

    /** Maps the class labels to index. */
    public int[] indexOf(int[] y) {
        int[] x = new int[y.length];
        for (int i = 0; i < y.length; i++) {
            x[i] = labels.indexOf(y[i]);
        }
        return x;
    }

    /**
     * Learns the class label mapping from samples.
     */
    public static ClassLabels fit(int[] y) {
        return fit(y, null);
    }

    /**
     * Learns the class label mapping from samples.
     */
    public static ClassLabels fit(int[] y, StructField field) {
        int[] labels = MathEx.unique(y);
        Arrays.sort(labels);
        int k = labels.length;

        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");
        }

        IntSet encoder = new IntSet(labels);
        if (labels[0] == 0 && labels[k-1] == k-1) {
            return new ClassLabels(k, y, encoder, field);
        } else {
            return new ClassLabels(k, Arrays.stream(y).map(yi -> encoder.indexOf(yi)).toArray(), encoder, field);
        }
    }

    /**
     * Learns the class label mapping from samples.
     */
    public static ClassLabels fit(BaseVector response) {
        int[] y = response.toIntArray();
        StructField field = response.field();

        @SuppressWarnings("unchecked")
        Optional<Measure> measure = response.measure();
        if (measure.isPresent() && measure.get() instanceof NominalScale) {
            NominalScale scale = (NominalScale) measure.get();
            int k = scale.size();
            int[] labels = IntStream.range(0, k).toArray();
            IntSet encoder = new IntSet(labels);
            return new ClassLabels(k, y, encoder, field);
        }

        return fit(y, field);
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
