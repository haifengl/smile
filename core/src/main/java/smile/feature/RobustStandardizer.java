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

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.type.StructType;

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
     * @param schema the schema of data.
     * @param median median.
     * @param iqr IQR.
     */
    public RobustStandardizer(StructType schema, double[] median, double[] iqr) {
        super(schema, median, iqr);
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param data The training data.
     */
    public static RobustStandardizer fit(DataFrame data) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Empty data frame");
        }

        StructType schema = data.schema();
        double[] median = new double[schema.length()];
        double[] iqr = new double[schema.length()];

        for (int i = 0; i < median.length; i++) {
            if (schema.field(i).isNumeric()) {
                final int col = i;
                final smile.sort.IQAgent agent = new smile.sort.IQAgent();
                // IQAgent is stateful and thus should not be used with parallel stream
                data.stream().sequential().forEach(t -> agent.add(t.getDouble(col)));
                median[i] = agent.quantile(0.5);
                iqr[i] = agent.quantile(0.75) - agent.quantile(0.25);
            }
        }

        return new RobustStandardizer(schema, median, iqr);
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param data The training data.
     */
    public static RobustStandardizer fit(double[][] data) {
        return fit(DataFrame.of(data));
    }

    @Override
    public String toString() {
        return IntStream.range(0, mu.length)
                .mapToObj(i -> String.format("%s[%.4f, %.4f]", schema.field(i).name, mu[i], std[i]))
                .collect(Collectors.joining(",", "RobustStandardizer(", ")"));
    }
}
