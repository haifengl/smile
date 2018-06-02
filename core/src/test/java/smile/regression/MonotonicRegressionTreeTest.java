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
package smile.regression;

import org.junit.Assert;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import smile.data.Attribute;
import smile.data.NumericAttribute;
import smile.math.Math;
import smile.stat.distribution.GaussianDistribution;

public class MonotonicRegressionTreeTest {

    @Test
    public void test() {
        // Example inspired by http://xgboost.readthedocs.io/en/latest/tutorials/monotonic.html

        int observations = 1000;
        double[][] x = new double[observations][];
        double[] y = new double[observations];

        for (int i = 0; i < observations; i++) {
            double[] features = Math.random(1);
            x[i] = features;
            y[i] = function(features[0]);
        }

        double[] monotonicRegression = new double[]{1};

        RegressionTree regressionTree = new RegressionTree(
                null, x, y, 100, 5, x[0].length, null, null,  null, monotonicRegression
        );

        double[] monotonicX = IntStream.range(0, 100).mapToDouble(i -> i / 200d).toArray();
        double[] preds = Arrays.stream(monotonicX).map(input -> {
            double[] features = new double[]{input};
            return regressionTree.predict(features);
        }).toArray();

        Set<Double> predsUniq = Arrays.stream(preds).boxed().collect(Collectors.toSet());
        Assert.assertTrue(
                "Sanity check failed - only one unique value in prediction array :" + predsUniq.iterator().next(),
                predsUniq.size() > 1
        );

        assertMonotonic(preds);
    }

    private double function(double x1) {
        return 5 * x1 + Math.sin(10 * Math.PI * x1) + GaussianDistribution.getInstance().rand();
    }

    private void assertMonotonic(double[] values) {
        if (values.length <= 1) {
            return;
        }

        for (int i = 1; i < values.length; i++) {
            double previous = values[i - 1];
            double current = values[i];
            Assert.assertTrue(
                    MessageFormat.format(
                            "Array {0} is not monotonic",
                            Arrays.stream(values).mapToObj(Double::toString).collect(Collectors.joining(",", "[", "]"))
                    ),
                    current >= previous
            );
        }
    }

}
