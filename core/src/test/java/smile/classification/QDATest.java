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

package smile.classification;

import smile.data.parser.ArffParser;
import smile.data.AttributeDataset;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import smile.math.MathEx;
import smile.validation.LOOCV;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng Li
 */
public class QDATest {

    public QDATest() {
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
     * Test of learn method, of class QDA.
     */
    @Test
    public void testLearn() {
        System.out.println("learn");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset iris = arffParser.parse(smile.util.Paths.getTestData("weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);

            int n = x.length;
            LOOCV loocv = new LOOCV(n);
            int error = 0;
            double[] posteriori = new double[3];
            for (int i = 0; i < n; i++) {
                double[][] trainx = MathEx.slice(x, loocv.train[i]);
                int[] trainy = MathEx.slice(y, loocv.train[i]);
                QDA qda = new QDA(trainx, trainy);

                if (y[loocv.test[i]] != qda.predict(x[loocv.test[i]], posteriori))
                    error++;
                
                //System.out.println(posteriori[0]+"\t"+posteriori[1]+"\t"+posteriori[2]);
            }

            System.out.println("QDA error = " + error);
            assertEquals(4, error);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}