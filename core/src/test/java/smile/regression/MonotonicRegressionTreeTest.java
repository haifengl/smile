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

package smile.regression;

import org.junit.Assert;
import org.junit.Test;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import smile.data.Attribute;
import smile.data.NumericAttribute;
import smile.math.MathEx;
import smile.stat.distribution.GaussianDistribution;

public class MonotonicRegressionTreeTest {

    @Test
    public void test() {
        // Example inspired by http://xgboost.readthedocs.io/en/latest/tutorials/monotonic.html

        int observations = 1000;
        double[][] x = new double[observations][];
        double[] y = new double[observations];

        for (int i = 0; i < observations; i++) {
            double[] features = MathEx.random(1);
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
        return 5 * x1 + MathEx.sin(10 * MathEx.PI * x1) + GaussianDistribution.getInstance().rand();
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
                            Arrays.toString(values)
                    ),
                    current >= previous
            );
        }
    }

}
