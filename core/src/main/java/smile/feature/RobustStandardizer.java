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

import smile.data.DataFrame;
import smile.data.type.DataType;
import smile.data.type.StructType;
import smile.math.MathEx;
import smile.sort.QuickSelect;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Robustly standardizes numeric feature by subtracting
 * the median and dividing by the IQR.
 *
 * @author Haifeng Li
 */
public class RobustStandardizer extends Standardizer {

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
            if (DataType.isDouble(schema.field(i).type)) {
                final smile.sort.IQAgent agent = new smile.sort.IQAgent();
                data.doubleVector(i).stream().forEach(agent::add);
                median[i] = agent.quantile(0.5);
                iqr[i] = agent.quantile(0.75) - agent.quantile(0.25);
            }
        }

        return new RobustStandardizer(schema, median, iqr);
    }

    @Override
    public String toString() {
        return IntStream.range(0, mu.length).mapToObj(
                i -> String.format("%s[%.4f, %.4f]", schema.field(i).name, mu[i], std[i])
        ).collect(Collectors.joining(",", "RobustStandardizer(", ")"));
    }
}
