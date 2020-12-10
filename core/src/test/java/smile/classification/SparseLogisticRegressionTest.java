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

package smile.classification;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.Arrays;
import smile.data.*;
import smile.util.SparseArray;
import smile.validation.metric.Error;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class SparseLogisticRegressionTest {

    public SparseLogisticRegressionTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
    }

    @Before
    public void setUp() {
    }

    @After
    public void tearDown() {
    }

    /** Returns the sparse data. */
    private SparseDataset sparse(double[][] x) {
        return SparseDataset.of(
                Arrays.stream(x).map(xi -> {
                    SparseArray a = new SparseArray();
                    for (int i = 0; i < xi.length; i++) {
                        if (xi[i] != 0.0) {
                            a.append(i, xi[i]);
                        }
                    }
                    return a;
                })
        );
    }

    @Test
    public void testUSPS() throws Exception {
        System.out.println("USPS");

        SparseDataset x = sparse(USPS.x);
        SparseDataset testx = sparse(USPS.testx);

        SparseLogisticRegression model = SparseLogisticRegression.fit(x, USPS.y, 0.3, 1E-3, 1000);

        int[] prediction = new int[testx.size()];
        for (int i = 0; i < testx.size(); i++) {
            prediction[i] = model.predict(testx.get(i));
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
                model.update(testx.get(i), USPS.testy[i]);
            }
            t += USPS.testx.length;
        }

        for (int i = 0; i < testx.size(); i++) {
            prediction[i] = model.predict(testx.get(i));
        }

        error = Error.of(USPS.testy, prediction);
        System.out.println("Error after online update = " + error);
        assertEquals(184, error);

        java.nio.file.Path temp = smile.data.Serialize.write(model);
        smile.data.Serialize.read(temp);
    }
}