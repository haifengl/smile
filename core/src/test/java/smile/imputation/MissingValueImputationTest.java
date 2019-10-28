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

package smile.imputation;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.data.Movement;
import smile.data.Segment;
import smile.data.SyntheticControl;
import smile.math.MathEx;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class MissingValueImputationTest {

    public MissingValueImputationTest() {
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

    private void impute(double[][] data, MissingValueImputation imputation, double rate, double expected) throws Exception {
        MathEx.setSeed(19650218); // to get repeatable results.

        int n = 0;
        double[][] dat = new double[data.length][data[0].length];
        for (int i = 0; i < dat.length; i++) {
            for (int j = 0; j < dat[i].length; j++) {
                if (MathEx.random() < rate) {
                    n++;
                    dat[i][j] = Double.NaN;
                } else {
                    dat[i][j] = data[i][j];                    
                }
            }
        }

        imputation.impute(dat);

        double error = 0.0;
        for (int i = 0; i < dat.length; i++) {
            for (int j = 0; j < dat[i].length; j++) {
                error += Math.abs(data[i][j] - dat[i][j]) / data[i][j];
            }
        }

        error = 100 * error / n;
        System.out.format("The error of %d%% missing values = %.2f%n", (int) (100 * rate),  error);
        assertEquals(expected, error, 1E-2);
    }

    @Test(expected = Test.None.class)
    public void testSyntheticControl() throws Exception {
        double[][] data = SyntheticControl.x;
        int p = data[0].length;
        System.out.println("----------- Synthetic Control ----------------");
        System.out.println("----------- " + data.length + " x " + p + " ----------------");
        System.out.println("AverageImputation");
        MissingValueImputation instance = new AverageImputation();
        impute(data, instance, 0.01, 25.91);
        impute(data, instance, 0.05, 31.17);
        impute(data, instance, 0.10, 29.31);
        impute(data, instance, 0.15, 29.21);
        impute(data, instance, 0.20, 27.81);
        impute(data, instance, 0.25, 29.38);

        System.out.println("KMeansImputation");
        instance = new KMeansImputation(10, 8);
        impute(data, instance, 0.01, 15.50);
        impute(data, instance, 0.05, 17.14);
        impute(data, instance, 0.10, 17.05);
        impute(data, instance, 0.15, 16.82);
        impute(data, instance, 0.20, 16.01);
        impute(data, instance, 0.25, 16.33);

        System.out.println("KNNImputation");
        instance = new KNNImputation(10);
        impute(data, instance, 0.01, 14.66);
        impute(data, instance, 0.05, 16.35);
        impute(data, instance, 0.10, 15.50);
        impute(data, instance, 0.15, 15.55);
        impute(data, instance, 0.20, 15.26);
        impute(data, instance, 0.25, 15.86);

        System.out.println("SVDImputation");
        instance = new SVDImputation(p / 5);
        impute(data, instance, 0.01, 13.50);
        impute(data, instance, 0.05, 15.84);
        impute(data, instance, 0.10, 14.94);
        // Matrix will be rank deficient with higher missing rate.

        System.out.println("LLSImputation");
        instance = new LLSImputation(10);
        impute(data, instance, 0.01, 14.66);
        impute(data, instance, 0.05, 16.35);
        impute(data, instance, 0.10, 15.50);
        impute(data, instance, 0.15, 15.55);
        impute(data, instance, 0.20, 15.26);
        impute(data, instance, 0.25, 15.86);
    }
}