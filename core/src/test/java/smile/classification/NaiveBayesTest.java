/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */

package smile.classification;

import java.util.stream.IntStream;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.stat.distribution.Distribution;
import smile.stat.distribution.GaussianMixture;
import smile.stat.distribution.EmpiricalDistribution;
import smile.test.data.Iris;
import smile.test.data.WeatherNominal;
import smile.util.IntSet;
import smile.validation.ClassificationMetrics;
import smile.validation.LOOCV;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class NaiveBayesTest {

    public NaiveBayesTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");

        int p = Iris.x[0].length;
        int k = MathEx.max(Iris.y) + 1;

        ClassificationMetrics metrics = LOOCV.classification(Iris.x, Iris.y, (x, y) -> {
            int n = x.length;
            double[] priori = new double[k];
            Distribution[][] condprob = new Distribution[k][p];
            for (int i = 0; i < k; i++) {
                priori[i] = 1.0 / k;
                final int c = i;
                for (int j = 0; j < p; j++) {
                    final int f = j;
                    double[] xi = IntStream.range(0, n).filter(l -> y[l] == c).mapToDouble(l -> x[l][f]).toArray();
                    condprob[i][j] = GaussianMixture.fit(3, xi);
                }
            }

            return new NaiveBayes(priori, condprob);
        });

        System.out.println(metrics);
        assertEquals(0.9533, metrics.accuracy, 1E-4);

        double[] priori = new double[k];
        Distribution[][] condprob = new Distribution[k][p];
        for (int i = 0; i < k; i++) {
            priori[i] = 1.0 / k;
            final int c = i;
            for (int j = 0; j < p; j++) {
                final int f = j;
                double[] xi = IntStream.range(0, Iris.x.length).filter(l -> Iris.y[l] == c).mapToDouble(l -> Iris.x[l][f]).toArray();
                condprob[i][j] = GaussianMixture.fit(3, xi);
            }
        }
        NaiveBayes model = new NaiveBayes(priori, condprob);
        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testWeather() {
        System.out.println("Weather");

        int p = WeatherNominal.level[0].length;
        int k = MathEx.max(WeatherNominal.y) + 1;

        ClassificationMetrics metrics = LOOCV.classification(WeatherNominal.level, WeatherNominal.y, (x, y) -> {
            int n = x.length;
            double[] priori = new double[k];
            Distribution[][] condprob = new Distribution[k][p];
            for (int i = 0; i < k; i++) {
                priori[i] = 1.0 / k;
                final int c = i;
                for (int j = 0; j < p; j++) {
                    final int f = j;
                    int[] xij = IntStream.range(0, n).filter(l -> y[l] == c).map(l -> (int) x[l][f]).toArray();
                    int[] xj = IntStream.range(0, n).map(l -> (int) x[l][f]).toArray();
                    // xij may miss some valid values after filtering. Use xj to capture all the values.
                    condprob[i][j] = EmpiricalDistribution.fit(xij, IntSet.of(xj));
                }
            }

            return new NaiveBayes(priori, condprob);
        });

        System.out.println(metrics);
        assertEquals(0.7857, metrics.accuracy, 1E-4);
    }
}
