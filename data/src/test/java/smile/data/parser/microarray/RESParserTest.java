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
package smile.data.parser.microarray;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import smile.data.Attribute;
import smile.data.AttributeDataset;

/**
 *
 * @author Haifeng Li
 */
public class RESParserTest {
    
    public RESParserTest() {
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
     * Test of parse method, of class RESParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("parse");
        RESParser parser = new RESParser();
        try {
            AttributeDataset data = parser.parse("RES", smile.data.parser.IOUtils.getTestDataFile("microarray/all_aml_test.res"));
            
            double[][] x = data.toArray(new double[data.size()][]);
            String[] id = data.toArray(new String[data.size()]);
            
            for (Attribute attribute : data.attributes()) {
                assertEquals(Attribute.Type.NUMERIC, attribute.getType());
                System.out.println(attribute.getName() + "\t" + attribute.getDescription());
            }

            assertEquals(7129, data.size());
            assertEquals(35, data.attributes().length);

            assertEquals("AFFX-BioB-5_at", id[0]);
            assertEquals(-214, x[0][0], 1E-7);
            assertEquals(-342, x[0][1], 1E-7);
            assertEquals(-87, x[0][2], 1E-7);

            assertEquals("Z78285_f_at", id[7128]);
            assertEquals(16, x[7128][32], 1E-7);
            assertEquals(-73, x[7128][33], 1E-7);
            assertEquals(-60, x[7128][34], 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
