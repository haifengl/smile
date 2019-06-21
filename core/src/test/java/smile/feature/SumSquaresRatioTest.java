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
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
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

    /**
     * Test of rank method, of class SumSquaresRatio.
     */
    @Test
    public void testRank() {
        System.out.println("rank");
        try {
            ArffParser arffParser = new ArffParser();
            arffParser.setResponseIndex(4);
            AttributeDataset iris = arffParser.parse(smile.util.Paths.getTestData("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);
            
            SumSquaresRatio ssr = new SumSquaresRatio();
            double[] ratio = ssr.rank(x, y);
            assertEquals(4, ratio.length);
            assertEquals(1.6226463, ratio[0], 1E-7);
            assertEquals(0.6444144, ratio[1], 1E-7);   
            assertEquals(16.0412833, ratio[2], 1E-7);   
            assertEquals(13.0520327, ratio[3], 1E-7); 
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of learn method, of class SumSquaresRatio.
     */
    @Test
    public void testLearn() {
        System.out.println("USPS");

        try {
            DelimitedTextParser parser = new DelimitedTextParser();
            parser.setResponseIndex(new NominalAttribute("class"), 0);
            AttributeDataset train = parser.parse("USPS Train", smile.util.Paths.getTestData("usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", smile.util.Paths.getTestData("usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);

            SumSquaresRatio ssr = new SumSquaresRatio();
            double[] score = ssr.rank(x, y);
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

            LDA lda = new LDA(xx, y);
            int[] prediction = new int[testn];
            for (int i = 0; i < testn; i++) {
                prediction[i] = lda.predict(testxx[i]);
            }

            double accuracy = new Accuracy().measure(testy, prediction);
            System.out.format("SSR %.2f%%%n", 100 * accuracy);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
