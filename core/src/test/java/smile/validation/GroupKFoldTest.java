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

import org.junit.Test;
import smile.math.MathEx;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;

/**
 *
 * @author Nejc Ilenic
 */
public class GroupKFoldTest {

    @Test
    public void testNoGroupsInSameFold() {
        int n = 10;
        int k = 3;
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};

        GroupKFold split = new GroupKFold(n, k, groups);

        for (int i = 0; i < k; i++) {
            int[] trainGroups = MathEx.unique(Arrays.stream(split.train[i]).map(x -> groups[x]).toArray());
            int[] testGroups = MathEx.unique(Arrays.stream(split.test[i]).map(x -> groups[x]).toArray());

            boolean anyTrainGroupInTestFold = Arrays.stream(trainGroups)
                    .anyMatch(trGroup -> Arrays.stream(testGroups).anyMatch(teGroup -> trGroup == teGroup));

            assertFalse(anyTrainGroupInTestFold);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidNParameter() {
        int n = -1;
        int k = 3;
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};
        GroupKFold split = new GroupKFold(n, k, groups);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidKParameter() {
        int n = 10;
        int k = -1;
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};
        GroupKFold split = new GroupKFold(n, k, groups);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGroupsParameter() {
        int n = 10;
        int k = 3;
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 4};
        GroupKFold split = new GroupKFold(n, k, groups);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGroupsNParameters() {
        int n = 9;
        int k = 3;
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};
        GroupKFold split = new GroupKFold(n, k, groups);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGroupsKParameters() {
        int n = 10;
        int k = 4;
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};
        GroupKFold split = new GroupKFold(n, k, groups);
    }
}
