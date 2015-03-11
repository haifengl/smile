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
import smile.data.BinarySparseDataset;

/**
 *
 * @author Haifeng Li
 */
public class BinarySparseDatasetParserTest {
    
    public BinarySparseDatasetParserTest() {
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
            BinarySparseDatasetParser parser = new BinarySparseDatasetParser();
            BinarySparseDataset data = parser.parse(this.getClass().getResourceAsStream("/smile/data/transaction/kosarak.dat"));
            assertEquals(990002, data.size());
            assertEquals(41271, data.ncols());
            assertEquals(1, data.get(0, 1));
            assertEquals(1, data.get(0, 2));
            assertEquals(1, data.get(0, 3));
            assertEquals(0, data.get(0, 4));
            assertEquals(1, data.get(990001, 1056));
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
