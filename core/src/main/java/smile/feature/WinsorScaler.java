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

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import smile.data.DataFrame;
import smile.data.type.DataType;
import smile.data.type.StructType;

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
     * Learns transformation parameters from a dataset with 5% lower limit
     * and 95% upper limit.
     * @param data The training data.
     */
    public static WinsorScaler fit(DataFrame data) {
        return fit(data, 0.05, 0.95);
    }

    /**
     * Learns transformation parameters from a dataset.
     * @param data The training data.
     * @param lower the lower limit in terms of percentiles of the original
     *              distribution (say 5th percentile).
     * @param upper the upper limit in terms of percentiles of the original
     *              distribution (say 95th percentile).
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
        double[] lo = new double[schema.length()];
        double[] hi = new double[schema.length()];

        for (int i = 0; i < lo.length; i++) {
            if (DataType.isDouble(schema.field(i).type)) {
                final smile.sort.IQAgent agent = new smile.sort.IQAgent();
                data.doubleVector(i).stream().forEach(agent::add);
                lo[i] = agent.quantile(lower);
                hi[i] = agent.quantile(upper);
            }
        }

        return new WinsorScaler(schema, lo, hi);
    }

    @Override
    public String toString() {
        return IntStream.range(0, lo.length).mapToObj(
                i -> String.format("%s[%.4f, %.4f]", schema.field(i).name, lo[i], hi[i])
        ).collect(Collectors.joining(",", "WinsorScaler(", ")"));
    }
}
