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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import smile.data.measure.Measure;
import smile.data.measure.NominalScale;
import smile.data.type.StructField;
import smile.data.vector.BaseVector;
import smile.math.MathEx;

/**
 * Class label encoder that arbitrary class labels to [0, k) or verse.
 *
 * @author Haifeng Li
 */
public class ClassLabel implements Serializable {
    private static final long serialVersionUID = 2L;

    /** Map of class id to original label. */
    private int[] labels;
    /** Map of class labels to id. */
    private Map<Integer, Integer> map;

    /**
     * Constructor.
     * @param labels the unique class labels.
     */
    public ClassLabel(int[] labels) {
        this.labels = labels;
        map = new HashMap<>();
        for (int i = 0; i < labels.length; i++) {
            map.put(labels[i], i);
        }
    }

    /** Returns the number of classes. */
    public int size() {
        return labels.length;
    }

    /** Returns the nominal scale for the class labels. */
    public NominalScale scale() {
        String[] values = new String[labels.length];
        for (int i = 0; i < labels.length; i++) {
            values[i] = String.valueOf(labels[i]);
        }
        return new NominalScale(values);
    }

    /**
     * Maps an class id to the corresponding label.
     *
     * @param id the class id.
     * @return the class label.
     */
    public int label(int id) {
        return labels[id];
    }

    /** Maps the class label to id. */
    public int id(int y) {
        return map.get(y);
    }

    /** Maps the class labels to id. */
    public int[] id(int[] y) {
        int[] x = new int[y.length];
        for (int i = 0; i < y.length; i++) {
            x[i] = map.get(y[i]);
        }
        return x;
    }

    /** The fitting results. */
    public static class Result {
        /** The number of classes. */
        public final int k;
        /** The class labels. If the class labels are already in [0, k), this is empty. */
        public final ClassLabel labels;
        /** The sample class id in [0, k). */
        public final int[] y;
        /** The optional meta data of response variable if the input is a vector. */
        public final Optional<StructField> field;

        /** Constructor. */
        public Result(int k, int[] y, ClassLabel labels) {
            this(k, y, labels, Optional.empty());
        }

        /** Constructor. */
        public Result(int k, int[] y, ClassLabel labels, Optional<StructField> field) {
            this.k = k;
            this.y = y;
            this.labels = labels;
            this.field = field;
        }
    }

    /**
     * Returns an identity class label mapping.
     * Note that the lookup operation in an identity
     * mapping is actually faster than branches.
     * Therefore, we prefer the identity mapping
     * to Optional or null checking.
     *
     * @param k the number of classes.
     */
    public static ClassLabel of(int k) {
        int[] labels = IntStream.range(0, k).toArray();
        return new ClassLabel(labels);
    }

    /**
     * Learns the class label mapping from sample.
     */
    public static Result fit(int[] y) {
        int[] labels = MathEx.unique(y);
        Arrays.sort(labels);
        int k = labels.length;

        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");
        }

        ClassLabel encoder = new ClassLabel(labels);
        if (labels[0] == 0 && labels[k-1] == k-1) {
            return new Result(k, y, encoder);
        } else {
            return new Result(k, encoder.id(y), encoder);
        }
    }

    /**
     * Learns the class label mapping from sample.
     */
    public static Result fit(BaseVector response) {
        int[] y = response.toIntArray();
        StructField field = response.field();

        Optional<Measure> measure = response.measure();
        if (measure.isPresent() && measure.get() instanceof NominalScale) {
            NominalScale scale = (NominalScale) measure.get();
            int k = scale.size();
            int[] labels = IntStream.range(0, k).toArray();
            ClassLabel encoder = new ClassLabel(labels);
            return new Result(k, y, encoder, Optional.of(field));
        }

        int[] labels = MathEx.unique(y);
        Arrays.sort(labels);
        int k = labels.length;

        if (k < 2) {
            throw new IllegalArgumentException("Only one class.");
        }

        ClassLabel encoder = new ClassLabel(labels);
        if (labels[0] == 0 && labels[k-1] == k-1) {
            return new Result(k, y, encoder, Optional.of(field));
        } else {
            field = new StructField(field.name, field.type, encoder.scale());
            return new Result(k, encoder.id(y), encoder, Optional.of(field));
        }
    }
}
