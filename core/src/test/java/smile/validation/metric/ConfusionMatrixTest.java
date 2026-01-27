/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.validation.metric;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class ConfusionMatrixTest {
    @Test
    public void test() {
        int[] truth = {0,1,2,3,4,5,0,1,2,3,4,5};
        int[] prediction = {0,1,2,4,5,2,1,2,4,5,4,1};

        ConfusionMatrix confusion = ConfusionMatrix.of(truth, prediction);
        System.out.println(confusion);

        int[][] matrix = confusion.matrix();
        int[] expected = {1,1,1,0,1,0};

        for(int i = 0; i < expected.length; i++){
            //main diagonal test
            assertEquals(matrix[i][i], expected[i]);
            //class 3 not predicted test
            assertEquals(matrix[i][3], 0);
        }
    }

}
