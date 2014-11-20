/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.feature;

import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;
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
public class Nominal2SparseBinaryTest {
    
    public Nominal2SparseBinaryTest() {
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
     * Test of f method, of class Nominal2SparseBinary.
     */
    @Test
    public void testF() {
        System.out.println("f");
        int[][] result = {
            {0, 3, 6, 9},
            {0, 3, 6, 8},
            {1, 3, 6, 9},
            {2, 4, 6, 9},
            {2, 5, 7, 9},
            {2, 5, 7, 8},
            {1, 5, 7, 8},
            {0, 4, 6, 9},
            {0, 5, 7, 9},
            {2, 4, 7, 9},
            {0, 4, 7, 8},
            {1, 4, 6, 8},
            {1, 3, 7, 9},
            {2, 4, 6, 8}
        };
        
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(this.getClass().getResourceAsStream("/smile/data/weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);
            
            Nominal2SparseBinary n2sb = new Nominal2SparseBinary(weather.attributes());
            for (int i = 0; i < x.length; i++) {
                int[] y = n2sb.f(x[i]);
                assertEquals(result[i].length, y.length);
                for (int j = 0; j < y.length; j++) {
                    assertEquals(result[i][j], y[j]);
                }
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
