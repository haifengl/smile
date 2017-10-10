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
public class Normalizer implements FeatureTransform {
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

    private Norm norm;

    /** Default constructor with L2 norm. */
    public Normalizer() {

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

        if (!Math.isZero(scale)) {
            for (int i = 0; i < x.length; i++) {
                x[i] /= scale;
            }
        }

        return x;
    }
}
