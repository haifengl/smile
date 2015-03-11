/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.math.distance;

import java.util.HashSet;
import java.util.Set;
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
public class JaccardDistanceTest {

    public JaccardDistanceTest() {
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
     * Test of distance method, of class JaccardDistance.
     */
    @Test
    public void testDistance() {
        System.out.println("distance");
        Set<Integer> a = new HashSet<Integer>();
        a.add(1);
        a.add(2);
        a.add(3);
        a.add(4);

        Set<Integer> b = new HashSet<Integer>();
        b.add(3);
        b.add(4);
        b.add(5);
        b.add(6);

        assertEquals(0.6666667, JaccardDistance.d(a, b), 1E-7);
    }
}