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

package smile.validation;

import static org.junit.Assert.*;

import org.junit.Test;

public class ConfusionMatrixTest {
    @Test
    public void test() {
        int[] truth = {0,1,2,3,4,5,0,1,2,3,4,5};
        int[] prediction = {0,1,2,4,5,2,1,2,4,5,4,1};

        ConfusionMatrix confusion = ConfusionMatrix.of(truth, prediction);
        System.out.println(confusion.toString());

        int[][] matrix = confusion.matrix;
        int[] expected = {1,1,1,0,1,0};

        for(int i = 0; i < expected.length; i++){
            //main diagonal test
            assertEquals(matrix[i][i], expected[i]);
            //class 3 not predicted test
            assertEquals(matrix[i][3], 0);
        }
    }

}
