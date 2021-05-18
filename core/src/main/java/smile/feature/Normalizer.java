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

package smile.feature;

import java.util.Optional;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.data.Tuple;
import smile.data.type.StructType;
import smile.math.MathEx;

/**
 * Normalize samples individually to unit norm. Each sample (i.e. each row of
 * the data matrix) with at least one non zero component is rescaled
 * independently of other samples so that its norm (L1 or L2) equals one.
 * <p>
 * Scaling inputs to unit norms is a common operation for text
 * classification or clustering for instance.
 *
 * @author Haifeng Li
 */
public enum Normalizer implements FeatureTransform {
    /**
     * Normalize L1 vector norm.
     */
    L1 {
        @Override
        public double[] transform(double[] x) {
            return scale(x, MathEx.norm1(x));
        }
    },

    /**
     * Normalize L2 vector norm.
     */
    L2 {
        @Override
        public double[] transform(double[] x) {
            return scale(x, MathEx.norm2(x));
        }
    },

    /**
     * Normalize L-infinity vector norm. Maximum absolute value.
     */
    L_INF {
        @Override
        public double[] transform(double[] x) {
            return scale(x, MathEx.normInf(x));
        }
    };

    @Override
    public Optional<StructType> schema() {
        return Optional.empty();
    }

    @Override
    public double transform(double x, int i) {
        throw new UnsupportedOperationException();
    }

    /**
     * Normalizes a vector.
     * @param x the vector.
     * @param norm the norm of vector.
     * @return a new vector of unit norm.
     */
    private static double[] scale(double[] x, double norm) {
        int p = x.length;
        double[] y = new double[p];
        if (MathEx.isZero(norm)) {
            System.arraycopy(x, 0, y, 0, p);
        } else {
            for (int i = 0; i < p; i++) {
                y[i] = x[i] / norm;
            }
        }

        return y;
    }

    @Override
    public double invert(double x, int i) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Tuple transform(Tuple x) {
        double[] y = transform(x.toArray(false, CategoricalEncoder.LEVEL));
        return Tuple.of(y, x.schema());
    }

    @Override
    public DataFrame transform(DataFrame data) {
        return DataFrame.of(data.stream().map(this::transform));
    }
}
