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
public class BestLocalizedWaveletTest {

    public BestLocalizedWaveletTest() {
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

    @Test
    public void testFilter() {
        System.out.println("filter");
        int[] order = {14, 18, 20};
        for (int p : order) {
            System.out.format("p = %d%n", p);
            double[] a = {.2, -.4, -.6, -.5, -.8, -.4, -.9, 0, -.2, .1, -.1, .1, .7, .9, 0, .3};
            double[] b = a.clone();
            Wavelet instance = new BestLocalizedWavelet(p);
            instance.transform(a);
            instance.inverse(a);
            for (int i = 0; i < a.length; i++) {
                assertEquals(b[i], a[i], 1E-7);
            }
        }
    }
}