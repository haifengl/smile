/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.feature;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;
import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.parser.ArffParser;

/**
 *
 * @author Haifeng
 */
public class Nominal2BinaryTest {
    
    public Nominal2BinaryTest() {
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
     * Test of attributes method, of class Nominal2Binary.
     */
    @SuppressWarnings("unused")
    @Test
    public void testAttributes() {
        System.out.println("attributes");
        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(this.getClass().getResourceAsStream("/smile/data/weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);
            
            Nominal2Binary n2b = new Nominal2Binary(weather.attributes());
            Attribute[] attributes = n2b.attributes();
            assertEquals(10, attributes.length);
            for (int i = 0; i < attributes.length; i++) {
                System.out.println(attributes[i]);
                assertEquals(Attribute.Type.NUMERIC, attributes[i].type);
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of f method, of class Nominal2Binary.
     */
    @Test
    public void testF() {
        System.out.println("f");
        double[][] result = {
            {1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0},
            {1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0},
            {0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0},
            {0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0},
            {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0},
            {1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0},
            {1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0},
            {0.0, 1.0, 0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0},
            {0.0, 1.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 1.0},
            {0.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0, 1.0, 0.0}
        };

        ArffParser arffParser = new ArffParser();
        arffParser.setResponseIndex(4);
        try {
            AttributeDataset weather = arffParser.parse(this.getClass().getResourceAsStream("/smile/data/weka/weather.nominal.arff"));
            double[][] x = weather.toArray(new double[weather.size()][]);
            
            Nominal2Binary n2b = new Nominal2Binary(weather.attributes());
            Attribute[] attributes = n2b.attributes();
            for (int i = 0; i < x.length; i++) {
                double[] y = new double[attributes.length];
                for (int j = 0; j < y.length; j++) {
                    y[j] = n2b.f(x[i], j);
                    assertEquals(result[i][j], y[j], 1E-7);
                }
            }
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
