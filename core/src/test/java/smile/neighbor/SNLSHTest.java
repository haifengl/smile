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
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;


/**
 * Test data set: http://research.microsoft.com/en-us/downloads/607d14d9-20cd-47e3-85bc-a2f65cd28042/
 * 
 * @author Qiyang Zuo
 * @since 03/31/15
 */
public class SNLSHTest {

    public SNLSHTest() {

    }

    @Before
    public void before() {

    }

    private String[] loadData(String path) throws IOException {
        return Files.lines(smile.util.Paths.getTestData(path)).skip(1).toArray(String[]::new);
    }

    @Test(expected = Test.None.class)
    public void test() throws IOException {
        System.out.println("SNLSH");

        String[] train = loadData("msrp/msr_paraphrase_train.txt");
        String[] test = loadData("msrp/msr_paraphrase_test.txt");

        SNLSH<String> lsh = createLSH(train);

        for (String s : test) {
            String[] t = tokenize(s);
            Neighbor[] neighbors = lsh.knn(t, 3);

            System.out.println("------------------");
            System.out.println(s);
            for (Neighbor n : neighbors) {
                System.out.println(train[n.index]);
            }
        }
    }

    private SNLSH<String> createLSH(String[] data) {
        SNLSH<String> lsh = new SNLSH<>(8);
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
