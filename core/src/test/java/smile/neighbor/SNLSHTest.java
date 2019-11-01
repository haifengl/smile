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

package smile.neighbor;

import org.junit.Before;
import org.junit.Test;
import smile.neighbor.lsh.SimHash;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Test data set: http://research.microsoft.com/en-us/downloads/607d14d9-20cd-47e3-85bc-a2f65cd28042/
 * 
 * @author Qiyang Zuo
 * @since 03/31/15
 */
public class SNLSHTest {
    private String[] texts = {
            "This is a test case",
            "This is another test case",
            "This is another test case too",
            "I want to be far from other cases"
    };

    public SNLSHTest() {

    }

    @Before
    public void before() {

    }

    @Test(expected = Test.None.class)
    public void test() throws IOException {
        System.out.println("SNLSH");

        SNLSH<String[], String> lsh = createLSH(texts);

        ArrayList<Neighbor<String[], String>> neighbors = new ArrayList<>();
        lsh.range(tokenize(texts[0]), 3, neighbors);
        assertEquals(2, neighbors.size());
        assertEquals(0, neighbors.get(0).index);
        assertEquals(1, neighbors.get(1).index);

        neighbors.clear();
        lsh.range(tokenize(texts[1]), 3, neighbors);
        assertEquals(2, neighbors.size());
        assertEquals(0, neighbors.get(0).index);
        assertEquals(1, neighbors.get(1).index);

        neighbors.clear();
        lsh.range(tokenize(texts[2]), 3, neighbors);
        assertEquals(1, neighbors.size());
        assertEquals(2, neighbors.get(0).index);

        neighbors.clear();
        lsh.range(tokenize(texts[3]), 3, neighbors);
        assertEquals(1, neighbors.size());
        assertEquals(3, neighbors.get(0).index);
    }

    private SNLSH<String[], String> createLSH(String[] data) {
        SNLSH<String[], String> lsh = new SNLSH<>(8, SimHash.text());
        for (String sentence : data) {
            String[] tokens = tokenize(sentence);
            lsh.put(tokens, sentence);
        }
        return lsh;
    }

    private String[] tokenize(String sentence) {
        return Arrays.stream(sentence.split("\\s+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .skip(3)
                .toArray(String[]::new);
    }
}
