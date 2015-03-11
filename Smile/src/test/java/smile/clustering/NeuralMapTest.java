/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.clustering;

import smile.validation.RandIndex;
import smile.validation.AdjustedRandIndex;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Haifeng
 */
public class NeuralMapTest {
    
    public NeuralMapTest() {
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
     * Test of learn method, of class NeuralMap.
     */
    @Test
    public void testUSPS() {
        System.out.println("USPS");
        DelimitedTextParser parser = new DelimitedTextParser();
        parser.setResponseIndex(new NominalAttribute("class"), 0);
        try {
            AttributeDataset train = parser.parse("USPS Train", this.getClass().getResourceAsStream("/smile/data/usps/zip.train"));
            AttributeDataset test = parser.parse("USPS Test", this.getClass().getResourceAsStream("/smile/data/usps/zip.test"));

            double[][] x = train.toArray(new double[train.size()][]);
            int[] y = train.toArray(new int[train.size()]);
            double[][] testx = test.toArray(new double[test.size()][]);
            int[] testy = test.toArray(new int[test.size()]);
            
            NeuralMap cortex = new NeuralMap(x[0].length, 8.0, 0.05, 0.0006, 5, 3);

            for (int i = 0; i < 5; i++) {
                for (double[] xi : x) {
                    cortex.update(xi);
                }
            }

            cortex.purge(16);
            cortex.partition(10);
            
            AdjustedRandIndex ari = new AdjustedRandIndex();
            RandIndex rand = new RandIndex();

            int[] p = new int[x.length];
            for (int i = 0; i < x.length; i++) {
                p[i] = cortex.predict(x[i]);
            }
            
            double r = rand.measure(y, p);
            double r2 = ari.measure(y, p);
            System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%\n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.65);
            assertTrue(r2 > 0.18);
            
            p = new int[testx.length];
            for (int i = 0; i < testx.length; i++) {
                p[i] = cortex.predict(testx[i]);
            }
            
            r = rand.measure(testy, p);
            r2 = ari.measure(testy, p);
            System.out.format("Testing rand index = %.2f%%\tadjusted rand index = %.2f%%\n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.65);
            assertTrue(r2 > 0.18);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
