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
public class LibsvmParserTest {
    
    public LibsvmParserTest() {
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
    @Test
    public void testParseNG20() throws Exception {
        System.out.println("NG20");
        LibsvmParser parser = new LibsvmParser();
        try {
            SparseDataset train = parser.parse("NG20 Train", smile.data.parser.IOUtils.getTestDataFile("libsvm/news20.dat"));
            SparseDataset test = parser.parse("NG20 Test", smile.data.parser.IOUtils.getTestDataFile("libsvm/news20.t.dat"));
            int[] y = train.toArray(new int[train.size()]);
            int[] testy = test.toArray(new int[test.size()]);
            
            assertEquals(train.size(), 15935);
            assertEquals(y[0], 0);
            assertEquals(train.get(0, 0), 0.0, 1E-7);
            assertEquals(train.get(0, 1), 0.0, 1E-7);
            assertEquals(train.get(0, 196), 2.0, 1E-7);
            assertEquals(train.get(0, 320), 3.0, 1E-7);
            assertEquals(train.get(0, 20504), 0.0, 1E-7);
            assertEquals(train.get(0, 20505), 1.0, 1E-7);
            assertEquals(train.get(0, 20506), 1.0, 1E-7);
            assertEquals(train.get(0, 20507), 0.0, 1E-7);

            assertEquals(y[y.length-1], 16);
            assertEquals(train.get(y.length-1, 0), 1.0, 1E-7);
            assertEquals(train.get(y.length-1, 1), 0.0, 1E-7);
            assertEquals(train.get(y.length-1, 9), 1.0, 1E-7);
            assertEquals(train.get(y.length-1, 10), 0.0, 1E-7);
            assertEquals(train.get(y.length-1, 57796), 0.0, 1E-7);
            assertEquals(train.get(y.length-1, 57797), 1.0, 1E-7);
            assertEquals(train.get(y.length-1, 57798), 0.0, 1E-7);
            
            assertEquals(test.size(), 3993);
            assertEquals(testy[0], 1);
            assertEquals(testy[testy.length-3], 17);
            assertEquals(testy[testy.length-2], 18);
            assertEquals(testy[testy.length-1], 16);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of parse method, of class LibsvmParser.
     */
    @Test
    public void testParseGlass() throws Exception {
        System.out.println("glass");
        LibsvmParser parser = new LibsvmParser();
        try {
            SparseDataset train = parser.parse("Glass", smile.data.parser.IOUtils.getTestDataFile("libsvm/glass.txt"));
            double[][] x = train.toArray();
            int[] y = train.toArray(new int[train.size()]);
            
            assertEquals(214, train.size());
            assertEquals(9, x[0].length);
            
            assertEquals(0, y[0]);
            assertEquals(-0.134323, x[0][0], 1E-7);
            assertEquals(-0.124812, x[0][1], 1E-7);
            assertEquals(1, x[0][2], 1E-7);
            assertEquals(-0.495327, x[0][3], 1E-7);
            assertEquals(-0.296429, x[0][4], 1E-7);
            assertEquals(-0.980676, x[0][5], 1E-7);
            assertEquals(-0.3829, x[0][6], 1E-7);
            assertEquals(-1, x[0][7], 1E-7);
            assertEquals(-1, x[0][8], 1E-7);
            
            assertEquals(5, y[213]);
            assertEquals(-0.476734, x[213][0], 1E-7);
            assertEquals(0.0526316, x[213][1], 1E-7);
            assertEquals(-1, x[213][2], 1E-7);
            assertEquals(0.115265, x[213][3], 1E-7);
            assertEquals(0.267857, x[213][4], 1E-7);
            assertEquals(-1, x[213][5], 1E-7);
            assertEquals(-0.407063, x[213][6], 1E-7);
            assertEquals(0.0603174, x[213][7], 1E-7);
            assertEquals(-1, x[213][8], 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
