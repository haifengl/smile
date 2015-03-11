/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.data.parser;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.SparseDataset;

/**
 *
 * @author Haifeng Li
 */
public class SparseDatasetParserTest {
    
    public SparseDatasetParserTest() {
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
     * Test of parse method, of class SparseDatasetParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("parse");
        try {
            SparseDatasetParser parser = new SparseDatasetParser(1);
            SparseDataset data = parser.parse(this.getClass().getResourceAsStream("/smile/data/text/kos.txt"));
            assertEquals(3430, data.size());
            assertEquals(6906, data.ncols());
            assertEquals(353160, data.length());
            assertEquals(2.0, data.get(0, 60), 1E-7);
            assertEquals(1.0, data.get(1, 1062), 1E-7);
            assertEquals(0.0, data.get(1, 1063), 1E-7);
            assertEquals(1.0, data.get(3429, 6821), 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
