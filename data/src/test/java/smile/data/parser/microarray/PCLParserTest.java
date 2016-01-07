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
public class PCLParserTest {
    
    public PCLParserTest() {
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
     * Test of parse method, of class PCLParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("parse");
        PCLParser parser = new PCLParser();
        try {
            AttributeDataset data = parser.parse("PCL", smile.data.parser.IOUtils.getTestDataFile("microarray/Dunham2002.pcl"));
            
            double[][] x = data.toArray(new double[data.size()][]);
            String[] id = data.toArray(new String[data.size()]);
            
            for (Attribute attribute : data.attributes()) {
                assertEquals(Attribute.Type.NUMERIC, attribute.getType());
                System.out.println(attribute.getName());
            }

            assertEquals(6694, data.size());
            assertEquals(16, data.attributes().length);

            assertEquals("YKR005C", id[0]);
            assertEquals(-0.43, x[0][0], 1E-7);
            assertEquals(-0.47, x[0][1], 1E-7);
            assertEquals(-0.39, x[0][2], 1E-7);

            assertEquals("YKR004C", id[6693]);
            assertEquals(0.03, x[6693][13], 1E-7);
            assertEquals(-0.53, x[6693][14], 1E-7);
            assertEquals(0.3, x[6693][15], 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
