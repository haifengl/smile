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
public class GCTParserTest {
    
    public GCTParserTest() {
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
     * Test of parse method, of class GCTParser.
     */
    @Test
    public void testParse() throws Exception {
        System.out.println("parse");
        GCTParser parser = new GCTParser();
        try {
            AttributeDataset data = parser.parse("GCT", smile.data.parser.IOUtils.getTestDataFile("microarray/allaml.dataset.gct"));
            
            double[][] x = data.toArray(new double[data.size()][]);
            String[] id = data.toArray(new String[data.size()]);
            
            for (Attribute attribute : data.attributes()) {
                assertEquals(Attribute.Type.NUMERIC, attribute.getType());
                System.out.println(attribute.getName());
            }

            assertEquals(12564, data.size());
            assertEquals(48, data.attributes().length);

            assertEquals("AFFX-MurIL2_at", id[0]);
            assertEquals(-161.8, x[0][0], 1E-7);
            assertEquals(-231.0, x[0][1], 1E-7);
            assertEquals(-279.0, x[0][2], 1E-7);

            assertEquals("128_at", id[12563]);
            assertEquals(95.0, x[12563][45], 1E-7);
            assertEquals(108.0, x[12563][46], 1E-7);
            assertEquals(346.0, x[12563][47], 1E-7);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }
}
