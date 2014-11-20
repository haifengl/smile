/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.wavelet;

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
public class D4WaveletTest {

    public D4WaveletTest() {
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
     * Test of filter method, of class D4Wavelet.
     */
    @Test
    public void testFilterD4() {
        System.out.println("filter");
        double[] a = {.2,-.4,-.6,-.5,-.8,-.4,-.9,0,-.2,.1,-.1,.1,.7,.9,0,.3};
        double[] b = a.clone();
        D4Wavelet instance = new D4Wavelet();
        instance.transform(a);
        instance.inverse(a);
        for (int i = 0; i < a.length; i++) {
            assertEquals(b[i], a[i], 1E-7);
        }
    }

}