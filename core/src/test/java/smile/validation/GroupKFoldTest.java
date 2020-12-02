/*******************************************************************************
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 ******************************************************************************/

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

        Split[] splits = CrossValidation.group(groups, k);

        for (int i = 0; i < k; i++) {
            int[] trainGroups = MathEx.unique(Arrays.stream(splits[i].train).map(x -> groups[x]).toArray());
            int[] testGroups = MathEx.unique(Arrays.stream(splits[i].test).map(x -> groups[x]).toArray());

            boolean anyTrainGroupInTestFold = Arrays.stream(trainGroups)
                    .anyMatch(trGroup -> Arrays.stream(testGroups).anyMatch(teGroup -> trGroup == teGroup));

            assertFalse(anyTrainGroupInTestFold);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidKParameter() {
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};
        CrossValidation.group(groups, -1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidGroupsKParameters() {
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};
        CrossValidation.group(groups, 4);
    }
}
