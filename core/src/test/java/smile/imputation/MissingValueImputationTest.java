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

    private double impute(double[][] data, MissingValueImputation imputation, double rate) throws Exception {

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

        double rmse = 0.0;
        for (int i = 0; i < dat.length; i++) {
            for (int j = 0; j < dat[i].length; j++) {
                double d = (data[i][j] - dat[i][j]);
                rmse += d * d;
            }
        }

        rmse = Math.sqrt(rmse / n);
        return rmse;
    }

    void impute(String name, double[][] data) throws Exception {
        int p = data[0].length;
        System.out.println("----------- " + name + " ----------------");
        System.out.println("----------- " + data.length + " x " + p + " ----------------");
        System.out.println("MeanImputation");
        MissingValueImputation instance = new AverageImputation();
        System.out.println("RMSE of 1% missing values = " + impute(data, instance, 0.01));
        System.out.println("RMSE of 5% missing values = " + impute(data, instance, 0.05));
        System.out.println("RMSE of 10% missing values = " + impute(data, instance, 0.10));
        System.out.println("RMSE of 15% missing values = " + impute(data, instance, 0.15));
        System.out.println("RMSE of 20% missing values = " + impute(data, instance, 0.20));
        System.out.println("RMSE of 25% missing values = " + impute(data, instance, 0.25));

        System.out.println("KMeansImputation");
        instance = new KMeansImputation(10, 5);
        System.out.println("RMSE of 1% missing values = " + impute(data, instance, 0.01));
        System.out.println("RMSE of 5% missing values = " + impute(data, instance, 0.05));
        System.out.println("RMSE of 10% missing values = " + impute(data, instance, 0.10));
        System.out.println("RMSE of 15% missing values = " + impute(data, instance, 0.15));
        System.out.println("RMSE of 20% missing values = " + impute(data, instance, 0.20));
        System.out.println("RMSE of 25% missing values = " + impute(data, instance, 0.25));

        System.out.println("KNNImputation");
        instance = new KNNImputation(10);
        System.out.println("RMSE of 1% missing values = " + impute(data, instance, 0.01));
        System.out.println("RMSE of 5% missing values = " + impute(data, instance, 0.05));
        System.out.println("RMSE of 10% missing values = " + impute(data, instance, 0.10));
        System.out.println("RMSE of 15% missing values = " + impute(data, instance, 0.15));
        System.out.println("RMSE of 20% missing values = " + impute(data, instance, 0.20));
        System.out.println("RMSE of 25% missing values = " + impute(data, instance, 0.25));

        if (p > 15) {
            System.out.println("SVDImputation");
            instance = new SVDImputation(p / 5);
            System.out.println("RMSE of 1% missing values = " + impute(data, instance, 0.01));
            System.out.println("RMSE of 5% missing values = " + impute(data, instance, 0.05));
            System.out.println("RMSE of 10% missing values = " + impute(data, instance, 0.10));
            // Matrix will be rank deficient.
            // System.out.println("RMSE of 15% missing values = " + impute(data, instance, 0.15));
            // System.out.println("RMSE of 20% missing values = " + impute(data, instance, 0.20));
            // System.out.println("RMSE of 25% missing values = " + impute(data, instance, 0.25));
        }

        if (p > 15) {
            System.out.println("LLSImputation");
            instance = new LLSImputation(10);
            System.out.println("RMSE of 1% missing values = " + impute(data, instance, 0.01));
            System.out.println("RMSE of 5% missing values = " + impute(data, instance, 0.05));
            System.out.println("RMSE of 10% missing values = " + impute(data, instance, 0.10));
            System.out.println("RMSE of 15% missing values = " + impute(data, instance, 0.15));
            System.out.println("RMSE of 20% missing values = " + impute(data, instance, 0.20));
            System.out.println("RMSE of 25% missing values = " + impute(data, instance, 0.25));
        }
    }

    /**
     * Test of impute method.
     */
    @Test
    public void testImpute() throws Exception {
        impute("Segment", Segment.x);
        impute("Movement", Movement.x);
        impute("Synthetic Control", SyntheticControl.x);
    }
}