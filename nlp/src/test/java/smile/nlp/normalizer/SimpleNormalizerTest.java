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

package smile.nlp.normalizer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Mark Arehart
 */
public class SimpleNormalizerTest {

    /**
     * Test of normalize method, of class SimpleNormalizer.
     */
    @Test
    public void testSplit() {
        System.out.println("normalize text");
        String text = "\tTHE BIG RIPOFF\n\n"
                + "Mr. John B. Smith bought cheapsite.com for 1.5 million dollars,\n\r"
                + "i.e. he paid far too much for it.\n\n"
                + "Did he mind?\n\r"
                + "   \t     \n"
                + "Adam Jones Jr. thinks \u201Che\u0301\u201D didn\u2019t.    \n\r\n"
                + "......\n"
                + "In any case, this isn't true... Well, with a probability of .9 it isn't. ";

        String expected = "THE BIG RIPOFF "
                + "Mr. John B. Smith bought cheapsite.com for 1.5 million dollars, "
                + "i.e. he paid far too much for it. "
                + "Did he mind? "
                + "Adam Jones Jr. thinks \"hé\" didn't. "
                + "...... "
                + "In any case, this isn't true... Well, with a probability of .9 it isn't.";

        String result = SimpleNormalizer.getInstance().normalize(text);

        assertEquals(expected, result);
    }
}
