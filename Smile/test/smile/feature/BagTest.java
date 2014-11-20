/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/
package smile.feature;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
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
public class BagTest {
    
    public BagTest() {
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
     * Test of feature method, of class Bag.
     */
    @Test
    public void testFeature() {
        System.out.println("feature");
        String[][] text = new String[2000][];

        BufferedReader input = new BufferedReader(new InputStreamReader(this.getClass().getResourceAsStream("/smile/data/text/movie.txt")));
        try {
            for (int i = 0; i < text.length; i++) {
                String[] words = input.readLine().trim().split("\\s+");
                text[i] = words;
            }
        } catch (IOException ex) {
            System.err.println(ex);
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                System.err.println(e);
            }
        }

        String[] feature = {
            "outstanding", "wonderfully", "wasted", "lame", "awful", "poorly",
            "ridiculous", "waste", "worst", "bland", "unfunny", "stupid", "dull",
            "fantastic", "laughable", "mess", "pointless", "terrific", "memorable",
            "superb", "boring", "badly", "subtle", "terrible", "excellent",
            "perfectly", "masterpiece", "realistic", "flaws"
        };
        
        Bag<String> bag = new Bag<String>(feature);
        
        double[][] x = new double[text.length][];
        for (int i = 0; i < text.length; i++) {
            x[i] = bag.feature(text[i]);
            assertEquals(feature.length, x[i].length);
        }
        
        assertEquals(1.0, x[0][15], 1E-7);
    }
}
