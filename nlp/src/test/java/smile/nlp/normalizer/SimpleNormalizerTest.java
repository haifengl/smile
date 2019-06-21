/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
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
