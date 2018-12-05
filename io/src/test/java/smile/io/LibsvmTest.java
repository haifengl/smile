/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.io;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.Dataset;
import smile.data.Instance;
import smile.math.SparseArray;

/**
 *
 * @author Haifeng Li
 */
public class LibsvmTest {
    
    public LibsvmTest() {
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
     * Test of parse method, of class LibsvmParser.
     */
    @Test(expected = Test.None.class)
    public void testParseNG20() throws Exception {
        System.out.println("NG20");
        DatasetReader reader = new DatasetReader();
        Dataset<Instance<SparseArray>> train = reader.libsvm(smile.util.Paths.getTestData("libsvm/news20.dat"));
        Dataset<Instance<SparseArray>> test  = reader.libsvm(smile.util.Paths.getTestData("libsvm/news20.t.dat"));

        assertEquals(15935, train.size());
        assertEquals(1, train.get(0).label());
        assertEquals(0.0, train.get(0).x().get(0), 1E-7);
        assertEquals(0.0, train.get(0).x().get(1), 1E-7);
        assertEquals(2.0, train.get(0).x().get(196), 1E-7);
        assertEquals(3.0, train.get(0).x().get(320), 1E-7);
        assertEquals(0.0, train.get(0).x().get(20504), 1E-7);
        assertEquals(1.0, train.get(0).x().get(20505), 1E-7);
        assertEquals(1.0, train.get(0).x().get(20506), 1E-7);
        assertEquals(0.0, train.get(0).x().get(20507), 1E-7);

        int n = train.size() - 1;
        assertEquals(17, train.get(n).label(), 16);
        assertEquals(1.0, train.get(n).x().get(0), 1E-7);
        assertEquals(0.0, train.get(n).x().get(1), 1E-7);
        assertEquals(1.0, train.get(n).x().get(9), 1E-7);
        assertEquals(0.0, train.get(n).x().get(10), 1E-7);
        assertEquals(0.0, train.get(n).x().get(57796), 1E-7);
        assertEquals(1.0, train.get(n).x().get(57797), 1E-7);
        assertEquals(0.0, train.get(n).x().get(57798), 1E-7);

        n = test.size();
        assertEquals(3993, test.size());
        assertEquals(2, test.get(0).label());
        assertEquals(18, test.get(n-3).label());
        assertEquals(19, test.get(n-2).label());
        assertEquals(17, test.get(n-1).label());
    }

    /**
     * Test of parse method, of class LibsvmParser.
     */
    @Test(expected = Test.None.class)
    public void testParseGlass() throws Exception {
        System.out.println("glass");
        DatasetReader reader = new DatasetReader();
        Dataset<Instance<SparseArray>> train = reader.libsvm(smile.util.Paths.getTestData("libsvm/glass.txt"));

        assertEquals(214, train.size());
        assertEquals(9, train.get(0).x().size());
            
        assertEquals( 1, train.get(0).label());
        assertEquals(-0.134323, train.get(0).x().get(0), 1E-7);
        assertEquals(-0.124812, train.get(0).x().get(1), 1E-7);
        assertEquals( 1.000000, train.get(0).x().get(2), 1E-7);
        assertEquals(-0.495327, train.get(0).x().get(3), 1E-7);
        assertEquals(-0.296429, train.get(0).x().get(4), 1E-7);
        assertEquals(-0.980676, train.get(0).x().get(5), 1E-7);
        assertEquals(-0.382900, train.get(0).x().get(6), 1E-7);
        assertEquals(-1.000000, train.get(0).x().get(7), 1E-7);
        assertEquals(-1.000000, train.get(0).x().get(8), 1E-7);
            
        assertEquals(7, train.get(213).label());
        assertEquals(-0.4767340, train.get(213).x().get(0), 1E-7);
        assertEquals( 0.0526316, train.get(213).x().get(1), 1E-7);
        assertEquals(-1.0000000, train.get(213).x().get(2), 1E-7);
        assertEquals( 0.1152650, train.get(213).x().get(3), 1E-7);
        assertEquals( 0.2678570, train.get(213).x().get(4), 1E-7);
        assertEquals(-1.0000000, train.get(213).x().get(5), 1E-7);
        assertEquals(-0.4070630, train.get(213).x().get(6), 1E-7);
        assertEquals( 0.0603174, train.get(213).x().get(7), 1E-7);
        assertEquals(-1.0000000, train.get(213).x().get(8), 1E-7);
    }
}
