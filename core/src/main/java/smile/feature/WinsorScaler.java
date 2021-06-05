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

import smile.data.DataFrame;
import smile.data.type.StructType;
import smile.sort.IQAgent;

/**
 * Scales all numeric variables into the range [0, 1].
 * If the dataset has outliers, normalization will certainly scale
 * the "normal" data to a very small interval. In this case, the
 * Winsorization procedure should be applied: values greater than the
 * specified upper limit are replaced with the upper limit, and those
 * below the lower limit are replace with the lower limit. Often, the
 * specified range is indicate in terms of percentiles of the original
 * distribution (like the 5th and 95th percentile).
 *
 * @author Haifeng Li
 */
public class WinsorScaler extends Scaler {
    private static final long serialVersionUID = 2L;

    /**
     * Constructor.
     * @param lo the lower bound.
     * @param hi the upper bound.
     */
    public WinsorScaler(double[] lo, double[] hi) {
        super(lo, hi);
    }

    /**
     * Constructor.
     * @param schema the schema of data.
     * @param lo the lower bound.
     * @param hi the upper bound.
     */
    public WinsorScaler(StructType schema, double[] lo, double[] hi) {
        super(schema, lo, hi);
    }

    /**
     * Fits the transformation parameters with 5% lower limit and 95% upper limit.
     * @param data The training data.
     * @return the model.
     */
    public static WinsorScaler fit(DataFrame data) {
        return fit(data, 0.05, 0.95);
    }

    /**
     * Fits the transformation parameters.
     * @param data The training data.
     * @param lower the lower limit in terms of percentiles of the original
     *              distribution (e.g. 5th percentile).
     * @param upper the upper limit in terms of percentiles of the original
     *              distribution (e.g. 95th percentile).
     * @return the model.
     */
    public static WinsorScaler fit(DataFrame data, double lower, double upper) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        if (lower < 0.0 || lower > 0.5) {
            throw new IllegalArgumentException("Invalid lower limit: " + lower);
        }

        if (upper < 0.5 || upper > 1.0) {
            throw new IllegalArgumentException("Invalid upper limit: " + upper);
        }

        if (upper <= lower) {
            throw new IllegalArgumentException("Invalid lower and upper limit pair: " + lower + " >= " + upper);
        }

        StructType schema = data.schema();
        int p = schema.length();
        double[] lo = new double[p];
        double[] hi = new double[p];

        for (int i = 0; i < p; i++) {
            if (schema.field(i).isNumeric()) {
                IQAgent agent = new IQAgent();
                double[] x = data.column(i).toDoubleArray();
                for (double xi : x) {
                    agent.add(xi);
                }
                lo[i] = agent.quantile(lower);
                hi[i] = agent.quantile(upper);
            }
        }

        return new WinsorScaler(schema, lo, hi);
    }

    /**
     * Fits the transformation parameters with 5% lower limit and 95% upper limit.
     * @param data The training data.
     * @return the model.
     */
    public static WinsorScaler fit(double[][] data) {
        return fit(data, 0.05, 0.95);
    }

    /**
     * Fits the transformation parameters.
     * @param data The training data.
     * @param lower the lower limit in terms of percentiles of the original
     *              distribution (say 5th percentile).
     * @param upper the upper limit in terms of percentiles of the original
     *              distribution (say 95th percentile).
     * @return the model.
     */
    public static WinsorScaler fit(double[][] data, double lower, double upper) {
        int p = data[0].length;
        double[] lo = new double[p];
        double[] hi = new double[p];

        IQAgent[] agents = new IQAgent[p];
        for (int i = 0; i < p; i++) {
            agents[i] = new IQAgent();
        }

        for (double[] x : data) {
            for (int i = 0; i < p; i++) {
                agents[i].add(x[i]);
            }
        }

        for (int i = 0; i < p; i++) {
            IQAgent agent = agents[i];
            lo[i] = agent.quantile(lower);
            hi[i] = agent.quantile(upper);
        }

        return new WinsorScaler(lo, hi);
    }
}
