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
import smile.data.SparseDataset;
import smile.data.SampleInstance;
import smile.datasets.USPS;
import smile.io.Read;
import smile.io.Write;
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
        return new SparseDataset<>(
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
        var usps = new USPS();
        double[][] x = usps.x();
        int[] y = usps.y();
        double[][] testx = usps.testx();
        int[] testy = usps.testy();
        SparseDataset<Integer> train = sparse(x, y);
        SparseDataset<Integer> test = sparse(testx, testy);

        SparseLogisticRegression model = SparseLogisticRegression.fit(train, new LogisticRegression.Options(0.3, 1E-3, 1000));

        int[] prediction = new int[test.size()];
        for (int i = 0; i < test.size(); i++) {
            prediction[i] = model.predict(test.get(i).x());
        }

        int error = Error.of(testy, prediction);
        System.out.println("Error = " + error);
        assertEquals(189, error);

        int t = x.length;
        int round = (int) Math.round(Math.log(testx.length));
        for (int loop = 0; loop < round; loop++) {
            double eta = 0.1 / t;
            System.out.format("Set learning rate at %.5f%n", eta);
            model.setLearningRate(eta);
            for (int i = 0; i < testx.length; i++) {
                model.update(test.get(i).x(), test.get(i).y());
            }
            t += testx.length;
        }

        for (int i = 0; i < test.size(); i++) {
            prediction[i] = model.predict(test.get(i).x());
        }

        error = Error.of(testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(188, error);

        java.nio.file.Path temp = Write.object(model);
        Read.object(temp);
    }
}