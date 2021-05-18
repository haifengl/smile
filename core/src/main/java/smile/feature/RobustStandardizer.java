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
 * Robustly standardizes numeric feature by subtracting
 * the median and dividing by the IQR.
 *
 * @author Haifeng Li
 */
public class RobustStandardizer extends Standardizer {
    private static final long serialVersionUID = 2L;

    /**
     * Constructor.
     * @param median median.
     * @param iqr IQR.
     */
    public RobustStandardizer(double[] median, double[] iqr) {
        super(median, iqr);
    }

    /**
     * Constructor.
     * @param schema the schema of data.
     * @param median median.
     * @param iqr IQR.
     */
    public RobustStandardizer(StructType schema, double[] median, double[] iqr) {
        super(schema, median, iqr);
    }

    /**
     * Fits the transformation parameters.
     * @param data The training data.
     * @return the model.
     */
    public static RobustStandardizer fit(DataFrame data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        int p = schema.length();
        double[] median = new double[p];
        double[] iqr = new double[p];

        for (int i = 0; i < p; i++) {
            if (schema.field(i).isNumeric()) {
                IQAgent agent = new IQAgent();
                double[] x = data.column(i).toDoubleArray();
                for (double xi : x) {
                    agent.add(xi);
                }
                median[i] = agent.quantile(0.5);
                iqr[i] = agent.quantile(0.75) - agent.quantile(0.25);
            }
        }

        return new RobustStandardizer(schema, median, iqr);
    }

    /**
     * Fits the transformation parameters.
     * @param data The training data.
     * @return the model.
     */
    public static RobustStandardizer fit(double[][] data) {
        int p = data[0].length;
        double[] median = new double[p];
        double[] iqr = new double[p];

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
            median[i] = agent.quantile(0.5);
            iqr[i] = agent.quantile(0.75) - agent.quantile(0.25);
        }

        return new RobustStandardizer(median, iqr);
    }
}
