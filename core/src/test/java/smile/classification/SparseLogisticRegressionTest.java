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
import smile.data.SparseDataset;
import smile.data.SampleInstance;
import smile.io.Read;
import smile.io.Write;
import smile.test.data.*;
import smile.util.SparseArray;
import smile.validation.metric.Error;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng
 */
public class SparseLogisticRegressionTest {

    public SparseLogisticRegressionTest() {
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

    /** Returns the sparse data. */
    private SparseDataset<Integer> sparse(double[][] x, int[] y) {
        return SparseDataset.of(
                IntStream.range(0, x.length).mapToObj(i -> {
                    double[] xi = x[i];
                    SparseArray a = new SparseArray();
                    for (int j = 0; j < xi.length; j++) {
                        if (xi[j] != 0.0) {
                            a.append(j, xi[j]);
                        }
                    }
                    return new SampleInstance<>(a, y[i]);
                }).toList()
        );
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        SparseDataset<Integer> train = sparse(USPS.x, USPS.y);
        SparseDataset<Integer> test = sparse(USPS.testx, USPS.testy);

        SparseLogisticRegression model = SparseLogisticRegression.fit(train, 0.3, 1E-3, 1000);

        int[] prediction = new int[test.size()];
        for (int i = 0; i < test.size(); i++) {
            prediction[i] = model.predict(test.get(i).x());
        }

        int error = Error.of(USPS.testy, prediction);
        System.out.println("Error = " + error);
        assertEquals(185, error);

        int t = USPS.x.length;
        int round = (int) Math.round(Math.log(USPS.testx.length));
        for (int loop = 0; loop < round; loop++) {
            double eta = 0.1 / t;
            System.out.format("Set learning rate at %.5f%n", eta);
            model.setLearningRate(eta);
            for (int i = 0; i < USPS.testx.length; i++) {
                model.update(test.get(i).x(), test.get(i).y());
            }
            t += USPS.testx.length;
        }

        for (int i = 0; i < test.size(); i++) {
            prediction[i] = model.predict(test.get(i).x());
        }

        error = Error.of(USPS.testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(184, error);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}