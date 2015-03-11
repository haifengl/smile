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
public class SignalNoiseRatioTest {
    
    public SignalNoiseRatioTest() {
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
     * Test of rank method, of class SignalNoiseRatio.
     */
    @Test
    public void testRank() {
        System.out.println("rank");
        try {
            ArffParser arffParser = new ArffParser();
            arffParser.setResponseIndex(4);
            AttributeDataset iris = arffParser.parse(this.getClass().getResourceAsStream("/smile/data/weka/iris.arff"));
            double[][] x = iris.toArray(new double[iris.size()][]);
            int[] y = iris.toArray(new int[iris.size()]);
            
            for (int i = 0; i < y.length; i++) {
                if (y[i] < 2) y[i] = 0;
                else y[i] = 1;
            }

            SignalNoiseRatio s2n = new SignalNoiseRatio();
            double[] ratio = s2n.rank(x, y);
            assertEquals(4, ratio.length);
            assertEquals(0.8743107, ratio[0], 1E-7);
            assertEquals(0.1502717, ratio[1], 1E-7);   
            assertEquals(1.3446912, ratio[2], 1E-7);   
            assertEquals(1.4757334, ratio[3], 1E-7); 
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
