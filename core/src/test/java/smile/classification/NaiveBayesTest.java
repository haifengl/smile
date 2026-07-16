/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.classification;

import java.util.stream.IntStream;
import smile.io.Read;
import smile.io.Write;
import smile.math.MathEx;
import smile.stat.distribution.Distribution;
import smile.stat.distribution.GaussianMixture;
import smile.stat.distribution.EmpiricalDistribution;
import smile.datasets.Iris;
import smile.datasets.WeatherNominal;
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
        MathEx.setSeed(19650218); // to get repeatable results.
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void testIris() throws Exception {
        System.out.println("Iris");

        var iris = new Iris();
        var data = iris.x();
        var label = iris.y();
        int p = data[0].length;
        int k = MathEx.max(label) + 1;

        ClassificationMetrics metrics = LOOCV.classification(data, label, (x, y) -> {
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
        assertEquals(0.9533, metrics.accuracy(), 1E-4);

        double[] priori = new double[k];
        Distribution[][] condprob = new Distribution[k][p];
        for (int i = 0; i < k; i++) {
            priori[i] = 1.0 / k;
            final int c = i;
            for (int j = 0; j < p; j++) {
                final int f = j;
                double[] xi = IntStream.range(0, data.length)
                        .filter(l -> label[l] == c)
                        .mapToDouble(l -> data[l][f])
                        .toArray();
                condprob[i][j] = GaussianMixture.fit(3, xi);
            }
        }
        NaiveBayes model = new NaiveBayes(priori, condprob);
        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }

    @Test
    public void testWeather() throws Exception {
        System.out.println("Weather");
        var weather = new WeatherNominal();
        double[][] level = weather.level();
        int[] play = weather.y();
        int p = level[0].length;
        int k = MathEx.max(play) + 1;

        ClassificationMetrics metrics = LOOCV.classification(level, play, (x, y) -> {
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
        assertEquals(0.7857, metrics.accuracy(), 1E-4);
    }

    @Test
    public void givenWrongSizeInput_whenPredict_thenThrowsIllegalArgumentException() throws Exception {
        // Given — build a 4-feature classifier from Iris
        var iris = new Iris();
        double[][] data = iris.x();
        int p = data[0].length;          // 4
        int k = MathEx.max(iris.y()) + 1;

        double[] priori = new double[k];
        smile.stat.distribution.Distribution[][] condprob = new smile.stat.distribution.Distribution[k][p];
        for (int i = 0; i < k; i++) {
            priori[i] = 1.0 / k;
            for (int j = 0; j < p; j++) {
                final int ii = i, jj = j;
                double[] xi = IntStream.range(0, data.length)
                        .filter(l -> iris.y()[l] == ii)
                        .mapToDouble(l -> data[l][jj])
                        .toArray();
                condprob[i][j] = smile.stat.distribution.GaussianDistribution.fit(xi);
            }
        }
        NaiveBayes model = new NaiveBayes(priori, condprob);

        // When / Then — wrong feature vector size
        assertThrows(IllegalArgumentException.class, () -> model.predict(new double[]{1.0, 2.0}));
    }

    @Test
    public void givenWrongPosterioriSize_whenPredict_thenThrowsIllegalArgumentException() throws Exception {
        // Given
        var iris = new Iris();
        double[][] data = iris.x();
        int p = data[0].length;
        int k = MathEx.max(iris.y()) + 1;

        double[] priori = new double[k];
        smile.stat.distribution.Distribution[][] condprob = new smile.stat.distribution.Distribution[k][p];
        for (int i = 0; i < k; i++) {
            priori[i] = 1.0 / k;
            for (int j = 0; j < p; j++) {
                final int ii = i, jj = j;
                double[] xi = IntStream.range(0, data.length)
                        .filter(l -> iris.y()[l] == ii)
                        .mapToDouble(l -> data[l][jj])
                        .toArray();
                condprob[i][j] = smile.stat.distribution.GaussianDistribution.fit(xi);
            }
        }
        NaiveBayes model = new NaiveBayes(priori, condprob);
        double[] x = data[0];

        // When / Then — wrong posteriori array size
        assertThrows(IllegalArgumentException.class, () -> model.predict(x, new double[k + 1]));
        assertThrows(IllegalArgumentException.class, () -> model.predict(x, new double[1]));
    }

    @Test
    public void givenInvalidPriori_whenConstructing_thenThrowsIllegalArgumentException() {
        // Given
        int k = 2, p = 3;
        smile.stat.distribution.Distribution[][] condprob = new smile.stat.distribution.Distribution[k][p];
        for (int i = 0; i < k; i++)
            for (int j = 0; j < p; j++)
                condprob[i][j] = smile.stat.distribution.GaussianDistribution.getInstance();

        // When / Then — priori that don't sum to 1
        double[] badPriori = {0.3, 0.3};
        assertThrows(IllegalArgumentException.class, () -> new NaiveBayes(badPriori, condprob));

        // priori with invalid individual value
        double[] badValue = {0.0, 1.0};
        assertThrows(IllegalArgumentException.class, () -> new NaiveBayes(badValue, condprob));
    }
}
