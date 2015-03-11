/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.distance;

import java.util.BitSet;
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
public class HammingDistanceTest {

    public HammingDistanceTest() {
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
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        int x = 0x5D;
        int y = 0x49;
        assertEquals(2, HammingDistance.d(x, y));
    }

    /**
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistanceArray() {
        System.out.println("distance");
        byte[] x = {1, 0, 1, 1, 1, 0, 1};
        byte[] y = {1, 0, 0, 1, 0, 0, 1};
        assertEquals(2, HammingDistance.d(x, y));
    }

    /**
     * Test of distance method, of class HammingDistance.
     */
    @Test
    public void testDistanceBitSet() {
        System.out.println("distance");

        BitSet x = new BitSet();
        x.set(1);
        x.set(3);
        x.set(4);
        x.set(5);
        x.set(7);

        BitSet y = new BitSet();
        y.set(1);
        y.set(4);
        y.set(7);

        assertEquals(2, HammingDistance.d(x, y), 1E-9);
    }
}