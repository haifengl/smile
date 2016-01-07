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
import smile.math.matrix.SparseMatrix;

/**
 *
 * @author Haifeng Li
 */
public class SparseMatrixParserTest {
    
    public SparseMatrixParserTest() {
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
     * Test of parse method, of class SparseMatrixParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("parse");
        try {
            SparseMatrixParser parser = new SparseMatrixParser();
            SparseMatrix data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("matrix/08blocks.txt"));
            assertEquals(592, data.size());
            assertEquals(300, data.nrows());
            assertEquals(300, data.ncols());
            assertEquals(94.0, data.get(36, 0), 1E-7);
            assertEquals(1.0, data.get(0, 1), 1E-7);
            assertEquals(33.0, data.get(36, 1), 1E-7);
            assertEquals(95.0, data.get(299, 299), 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    /**
     * Test of parse method, of class SparseMatrixParser.
     */
    @Test
    public void testParseExchange() throws Exception {
        System.out.println("HB exchange format");
        try {
            SparseMatrixParser parser = new SparseMatrixParser();
            SparseMatrix data = parser.parse(smile.data.parser.IOUtils.getTestDataFile("matrix/5by5_rua.hb"));
            assertEquals(13, data.size());
            assertEquals(5, data.nrows());
            assertEquals(5, data.ncols());
            assertEquals(11.0, data.get(0, 0), 1E-7);
            assertEquals(31.0, data.get(2, 0), 1E-7);
            assertEquals(51.0, data.get(4, 0), 1E-7);
            assertEquals(55.0, data.get(4, 4), 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
