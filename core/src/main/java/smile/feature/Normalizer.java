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

package smile.feature;

import smile.data.DataFrame;
import smile.data.Tuple;
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
public class Normalizer implements FeatureTransform {
    private static final long serialVersionUID = 2L;

    /**
     * The types of data scaling.
     */
    public enum Norm {
        /**
         * L1 vector norm.
         */
        L1,
        /**
         * L2 vector norm.
         */
        L2,
        /**
         * L-infinity vector norm. Maximum absolute value.
         */
        Inf
    }

    /** The type of norm .*/
    private Norm norm;

    /** Default constructor with L2 norm. */
    public Normalizer() {
        this(Norm.L2);
    }

    /**
     * Constructor.
     * @param norm The norm to use to normalize each non zero sample.
     */
    public Normalizer(Norm norm) {
        this.norm = norm;
    }

    @Override
    public double[] transform(double[] x) {
        double scale;

        switch (norm) {
            case L1:
                scale = MathEx.norm1(x);
                break;
            case L2:
                scale = MathEx.norm2(x);
                break;
            case Inf:
                scale = MathEx.normInf(x);
                break;
            default:
                throw new IllegalStateException("Unknown type of norm: " + norm);
        }

        double[] y = new double[x.length];
        if (MathEx.isZero(scale)) {
            System.arraycopy(x, 0, y, 0, x.length);
        } else {
            for (int i = 0; i < x.length; i++) {
                y[i] = x[i] / scale;
            }
        }

        return y;
    }

    @Override
    public Tuple transform(Tuple x) {
        double[] y = transform(x.toArray());
        return Tuple.of(y, x.schema());
    }

    @Override
    public DataFrame transform(DataFrame data) {
        return DataFrame.of(data.stream().map(this::transform));
    }

    @Override
    public String toString() {
        return String.format("Normalizer(%s)", norm);
    }
}
