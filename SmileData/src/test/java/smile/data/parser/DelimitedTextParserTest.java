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
import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.NominalAttribute;

/**
 *
 * @author Haifeng Li
 */
public class DelimitedTextParserTest {
    
    public DelimitedTextParserTest() {
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
     * Test of parse method, of class DelimitedTextParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("parse");
        try {
            DelimitedTextParser parser = new DelimitedTextParser();
            parser.setResponseIndex(new NominalAttribute("class"), 0);
            AttributeDataset usps = parser.parse("USPS Train", this.getClass().getResourceAsStream("/smile/data/usps/zip.train"));
            double[][] x = usps.toArray(new double[usps.size()][]);
            int[] y = usps.toArray(new int[usps.size()]);
            
            assertEquals(Attribute.Type.NOMINAL, usps.response().type);
            for (Attribute attribute : usps.attributes()) {
                assertEquals(Attribute.Type.NUMERIC, attribute.type);
            }

            assertEquals(7291, usps.size());
            assertEquals(256, usps.attributes().length);

            assertEquals("6", usps.response().toString(y[0]));
            assertEquals("5", usps.response().toString(y[1]));
            assertEquals("4", usps.response().toString(y[2]));
            assertEquals(-1.0000, x[0][6], 1E-7);
            assertEquals(-0.6310, x[0][7], 1E-7);
            assertEquals(0.8620, x[0][8], 1E-7);

            assertEquals("1", usps.response().toString(y[7290]));            
            assertEquals(-1.0000, x[7290][4], 1E-7);
            assertEquals(-0.1080, x[7290][5], 1E-7);
            assertEquals(1.0000, x[7290][6], 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
