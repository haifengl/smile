/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.clustering;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.SparseDataset;
import smile.data.parser.LibsvmParser;
import smile.validation.AdjustedRandIndex;
import smile.validation.RandIndex;

/**
 *
 * @author Haifeng
 */
public class SIBTest {
    
    public SIBTest() {
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
     * Test of parse method, of class SIB.
     */
    @Test
    public void testParseNG20() throws Exception {
        System.out.println("NG20");
        LibsvmParser parser = new LibsvmParser();
        try {
            SparseDataset train = parser.parse("NG20 Train", this.getClass().getResourceAsStream("/smile/data/libsvm/news20.dat"));
            SparseDataset test = parser.parse("NG20 Test", this.getClass().getResourceAsStream("/smile/data/libsvm/news20.t.dat"));
            int[] y = train.toArray(new int[train.size()]);
            int[] testy = test.toArray(new int[test.size()]);
            
            SIB sib = new SIB(train, 20, 100, 8);
            System.out.println(sib);
            
            AdjustedRandIndex ari = new AdjustedRandIndex();
            RandIndex rand = new RandIndex();
            double r = rand.measure(y, sib.getClusterLabel());
            double r2 = ari.measure(y, sib.getClusterLabel());
            System.out.format("Training rand index = %.2f%%\tadjusted rand index = %.2f%%\n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.85);
            assertTrue(r2 > 0.2);
            
            int[] p = new int[test.size()];
            for (int i = 0; i < test.size(); i++) {
                p[i] = sib.predict(test.get(i).x);
            }
            
            r = rand.measure(testy, p);
            r2 = ari.measure(testy, p);
            System.out.format("Testing rand index = %.2f%%\tadjusted rand index = %.2f%%\n", 100.0 * r, 100.0 * r2);
            assertTrue(r > 0.85);
            assertTrue(r2 > 0.2);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
