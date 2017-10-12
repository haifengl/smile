/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package smile.feature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.data.Attribute;
import smile.math.Math;

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
public class Normalizer extends FeatureTransform {
    private static final Logger logger = LoggerFactory.getLogger(Normalizer.class);

    /**
     * The types of data scaling.
     */
    public static enum Norm {
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
    private Norm norm = Norm.L2;

    /** Default constructor with L2 norm. */
    public Normalizer() {

    }

    /**
     * Constructor with L2 norm.
     * @param copy  If false, try to avoid a copy and do inplace scaling instead.
     */
    public Normalizer(boolean copy) {
        super(copy);
    }

    /**
     * Constructor.
     * @param norm The norm to use to normalize each non zero sample.
     */
    public Normalizer(Norm norm) {
        this.norm = norm;
    }

    /**
     * Constructor.
     * @param norm The norm to use to normalize each non zero sample.
     * @param copy  If false, try to avoid a copy and do inplace scaling instead.
     */
    public Normalizer(Norm norm, boolean copy) {
        super(copy);
        this.norm = norm;
    }

    @Override
    public void learn(Attribute[] attributes, double[][] data) {
        logger.info("Normalizer is stateless and learn() does nothing.");
    }

    @Override
    public double[] transform(double[] x) {
        double scale;

        switch (norm) {
            case L1:
                scale = Math.norm1(x);
                break;
            case L2:
                scale = Math.norm2(x);
                break;
            case Inf:
                scale = Math.normInf(x);
                break;
            default:
                throw new IllegalStateException("Unknown type of norm: " + norm);
        }

        double[] y = copy ? new double[x.length] : x;
        if (Math.isZero(scale)) {
            if (y != x) {
                System.arraycopy(x, 0, y, 0, x.length);
            }
        } else {
            for (int i = 0; i < x.length; i++) {
                y[i] = x[i] / scale;
            }
        }

        return y;
    }

    @Override
    public String toString() {
        return "Normalizer()";
    }
}
