/******************************************************************************
 *                   Confidential Proprietary                                 *
 *         (c) Copyright Haifeng Li 2011, All Rights Reserved                 *
 ******************************************************************************/

package smile.sort;

import smile.math.Math;
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
public class IQAgentTest {

    public IQAgentTest() {
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
     * Test of add method, of class IQAgent.
     */
    @Test
    public void testAdd() {
        System.out.println("IQAgent");

        double[] data = new double[100000];
        for (int i = 0; i < data.length; i++)
            data[i] = i+1;

        Math.permutate(data);
        
        IQAgent instance = new IQAgent();
        for (int i = 0; i < data.length; i++)
            instance.add(data[i]);

        for (int i = 1; i <= 100; i++) {
            System.out.println(i + "%\t" + instance.quantile(i/100.0) + "\t" + Math.abs(1-instance.quantile(i/100.0)/(i*1000)));
            assertTrue(Math.abs(1-instance.quantile(i/100.0)/(i*1000)) < 0.01);
        }
    }
}