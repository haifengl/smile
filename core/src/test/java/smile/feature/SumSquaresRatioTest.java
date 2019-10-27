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

package smile.feature;

import smile.validation.Accuracy;
import smile.classification.LDA;
import smile.data.Iris;
import smile.data.USPS;
import smile.sort.QuickSort;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class SumSquaresRatioTest {
    
    public SumSquaresRatioTest() {
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

    @Test
    public void test() {
        System.out.println("SumSquaresRatio");
        double[] ratio = SumSquaresRatio.of(Iris.x, Iris.y);
        assertEquals(4, ratio.length);
        assertEquals( 1.6226463, ratio[0], 1E-6);
        assertEquals( 0.6444144, ratio[1], 1E-6);
        assertEquals(16.0412833, ratio[2], 1E-6);
        assertEquals(13.0520327, ratio[3], 1E-6);
    }

    @Test
    public void tesUSPS() {
        System.out.println("USPS");

        double[][] x = USPS.x;
        int[] y = USPS.y;
        double[][] testx = USPS.testx;
        int[] testy = USPS.testy;

        double[] score = SumSquaresRatio.of(x, y);
        int[] index = QuickSort.sort(score);

        int p = 135;
        int n = x.length;
        double[][] xx = new double[n][p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < n; i++) {
                xx[i][j] = x[i][index[255-j]];
            }
        }

        int testn = testx.length;
        double[][] testxx = new double[testn][p];
        for (int j = 0; j < p; j++) {
            for (int i = 0; i < testn; i++) {
                testxx[i][j] = testx[i][index[255-j]];
            }
        }

        LDA lda = LDA.fit(xx, y);
        int[] prediction = new int[testn];
        for (int i = 0; i < testn; i++) {
            prediction[i] = lda.predict(testxx[i]);
        }

        double accuracy = new Accuracy().measure(testy, prediction);
        System.out.format("SSR %.2f%%%n", 100 * accuracy);
    }
}
