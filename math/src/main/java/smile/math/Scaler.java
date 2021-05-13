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

package smile.math;

import smile.sort.IQAgent;

/**
 * Affine transformation {@code y = (x - offset) / scale}.
 *
 * @author Haifeng Li
 */
public class Scaler implements Function {
    private static final long serialVersionUID = 2L;

    /**
     * The offset.
     */
    private double scale;

    /**
     * The scaling factor.
     */
    private double offset;

    /**
     * If true, clip the value in [0, 1].
     */
    private boolean clip;

    /**
     * Constructor.
     * @param scale the scaling factor.
     * @param offset the offset.
     */
    public Scaler(double scale, double offset, boolean clip) {
        this.scale = MathEx.isZero(scale) ? 1.0 : scale;
        this.offset = offset;
        this.clip = clip;
    }

    @Override
    public double f(double x) {
        double y = (x - offset) / scale;
        if (clip) {
            if (y < 0.0) y = 0.0;
            if (y > 1.0) y = 1.0;
        }
        return y;
    }

    @Override
    public double inv(double x) {
        return x * scale + offset;
    }

    /**
     * Returns the scaler that map the values into the range [0, 1].
     * @param data the training data.
     * @return the scaler.
     */
    public static Scaler of(double[] data) {
        return new Scaler(MathEx.min(data), MathEx.max(data), true);
    }

    /**
     * Returns the scaler that map the values into the range [0, 1].
     * The values greater than the 95% percentile are replaced
     * with the upper limit, and those below the 5% percentile are
     * replace with the lower limit.
     *
     * @param data the training data.
     * @return the scaler.
     */
    public static Scaler winsor(double[] data) {
        return winsor(data, 0.05, 0.95);
    }

    /**
     * Returns the scaler that map the values into the range [0, 1].
     * The values greater than the specified upper limit are replaced
     * with the upper limit, and those below the lower limit are
     * replace with the lower limit.
     *
     * @param data the training data.
     * @param lower the lower limit in terms of percentiles of the original
     *              distribution (e.g. 5th percentile).
     * @param upper the upper limit in terms of percentiles of the original
     *              distribution (e.g. 95th percentile).
     * @return the scaler.
     */
    public static Scaler winsor(double[] data, double lower, double upper) {
        IQAgent agent = new IQAgent();
        for (double x : data) {
            agent.add(x);
        }

        return new Scaler(agent.quantile(lower), agent.quantile(upper), true);
    }

    /**
     * Returns the standardize scaler to 0 mean and unit variance.
     * @param data The training data.
     * @return the scaler.
     */
    public static Scaler standardizer(double[] data) {
        return standardizer(data, false);
    }

    /**
     * Returns the standardize scaler to 0 mean and unit variance.
     * @param data The training data.
     * @param robust If true, scale by subtracting the median and dividing by the IQR.
     * @return the scaler.
     */
    public static Scaler standardizer(double[] data, boolean robust) {
        if (robust) {
            IQAgent agent = new IQAgent();
            for (double x : data) {
                agent.add(x);
            }

            double median = agent.quantile(0.5);
            double iqr = agent.quantile(0.75) - agent.quantile(0.25);
            return new Scaler(median, iqr, false);
        } else {
            return new Scaler(MathEx.mean(data), MathEx.sd(data), false);
        }
    }
}
