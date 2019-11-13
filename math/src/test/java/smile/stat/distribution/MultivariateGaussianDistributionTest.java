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

package smile.stat.distribution;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import smile.math.matrix.Matrix;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class MultivariateGaussianDistributionTest {

    double[] mu = {1.0, 0.0, -1.0};
    double[][] sigma = {
        {0.9000, 0.4000, 0.7000},
        {0.4000, 0.5000, 0.3000},
        {0.7000, 0.3000, 0.8000}
    };
    double[][] x = {
        {1.2793, -0.1029, -1.5852},
        {-0.2676, -0.1717, -1.8695},
        {1.6777, 0.7642, -1.0226},
        {2.5402, 1.0887, 0.8989},
        {0.3437, 0.4407, -1.9424},
        {1.8140, 0.7413, -0.1129},
        {2.1897, 1.2047, 0.0128},
        {-0.5119, -1.3545, -2.6181},
        {-0.3670, -0.6188, -3.1594},
        {1.5418, 0.1519, -0.6054}
    };
    double[] pdf = {
        0.0570, 0.0729, 0.0742, 0.0178, 0.0578,
        0.1123, 0.0511, 0.0208, 0.0078, 0.1955
    };
    double[] cdf = {
        0.1752, 0.0600, 0.4545, 0.9005, 0.1143,
        0.6974, 0.8178, 0.0050, 0.0051, 0.4419
    };

    public MultivariateGaussianDistributionTest() {
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

    /**
     * Test of constructor, of class MultivariateGaussianDistribution.
     */
    @Test
    public void testMultivariateGaussianDistribution() {
        System.out.println("MultivariateGaussianDistribution");
        MathEx.setSeed(19650218); // to get repeatable results.
        MultivariateGaussianDistribution instance = new MultivariateGaussianDistribution(mu, sigma[0]);
        double[][] data = instance.rand(2000);
        MultivariateGaussianDistribution est = MultivariateGaussianDistribution.fit(data, true);
        assertArrayEquals(mu, est.mean(), 5E-2);
        for (int i = 0; i < mu.length; i++) {
            assertEquals(sigma[0][i], est.sigma.get(i, i), 5E-2);
            for (int j = 0; j < mu.length; j++) {
                if (i != j) {
                    assertEquals(0, est.sigma.get(i, j), 1E-10);
                }
            }
        }

        instance = new MultivariateGaussianDistribution(mu, Matrix.of(sigma));
        data = instance.rand(2000);
        est = MultivariateGaussianDistribution.fit(data);
        assertArrayEquals(mu, est.mean(), 5E-2);

        for (int i = 0; i < mu.length; i++) {
            for (int j = 0; j < mu.length; j++) {
                assertEquals(sigma[i][j], est.sigma.get(i, j), 5E-2);
            }
        }

        est = MultivariateGaussianDistribution.fit(data, true);
        assertArrayEquals(mu, est.mean(), 5E-2);
        for (int i = 0; i < mu.length; i++) {
            for (int j = 0; j < mu.length; j++) {
                if (i == j) {
                    assertEquals(sigma[i][j], est.sigma.get(i, j), 5E-2);
                } else {
                    assertEquals(0.0, est.sigma.get(i, j), 1E-10);
                }
            }
        }
    }

    /**
     * Test of isDiagonal method, of class MultivariateGaussian.
     */
    @Test
    public void testDiagonal() {
        System.out.println("diagonal");
        MultivariateGaussianDistribution instance = new MultivariateGaussianDistribution(mu, 1.0);
        assertEquals(true, instance.diagonal);

        instance = new MultivariateGaussianDistribution(mu, sigma[0]);
        assertEquals(true, instance.diagonal);

        instance = new MultivariateGaussianDistribution(mu, Matrix.of(sigma));
        assertEquals(false, instance.diagonal);
    }

    /**
     * Test of length method, of class MultivariateGaussian.
     */
    @Test
    public void testLength() {
        System.out.println("length");
        MultivariateGaussianDistribution instance = new MultivariateGaussianDistribution(mu, 1.0);
        assertEquals(4, instance.length());

        instance = new MultivariateGaussianDistribution(mu, sigma[0]);
        assertEquals(6, instance.length());

        instance = new MultivariateGaussianDistribution(mu, Matrix.of(sigma));
        assertEquals(9, instance.length());
    }

    /**
     * Test of entropy method, of class MultivariateGaussian.
     */
    @Test
    public void testEntropy() {
        System.out.println("entropy");
        MultivariateGaussianDistribution instance = new MultivariateGaussianDistribution(mu, Matrix.of(sigma));
        assertEquals(2.954971, instance.entropy(), 1E-6);
    }

    /**
     * Test of pdf method, of class MultivariateGaussian.
     */
    @Test
    public void testPdf() {
        System.out.println("pdf");
        MultivariateGaussianDistribution instance = new MultivariateGaussianDistribution(mu, Matrix.of(sigma));
        for (int i = 0; i < x.length; i++) {
            assertEquals(pdf[i], instance.p(x[i]), 1E-4);
        }
    }

    /**
     * Test of cdf method, of class MultivariateGaussian.
     */
    @Test
    public void testCdf() {
        System.out.println("cdf");
        MultivariateGaussianDistribution instance = new MultivariateGaussianDistribution(mu, Matrix.of(sigma));
        for (int i = 0; i < x.length; i++) {
            assertEquals(cdf[i], instance.cdf(x[i]), 1E-2);
        }
    }

    /**
     * Test of cdf method, of class MultivariateGaussian.
     */
    @Test
    public void testCdf2() {
        System.out.println("cdf2");
        double[][] S = {
                {3.260127902272362, 2.343938296424249, 0.1409050254343716, -0.1628775438743266},
                {2.343938296424249, 4.213034991388330, 1.3997210599608563,  0.3373448510018783},
                {0.1409050254343716, 1.3997210599608563, 4.6042485263677939,  0.0807267064408651},
                {-0.1628775438743266, 0.3373448510018783, 0.0807267064408651,  5.4950949215890672}
        };

        double[] M = {-0.683477474844462,  1.480296478403701,  1.008431991316523,  0.448404211078558};
        double[] X = {0.713919336274493, 0.584408785741822, 0.263119200077829, 0.732513610871908};

        MultivariateGaussianDistribution instance = new MultivariateGaussianDistribution(M, Matrix.of(S));

        // The expected value is based on R
        assertEquals(0.0904191282120575, instance.cdf(X), 1E-3);
    }
}