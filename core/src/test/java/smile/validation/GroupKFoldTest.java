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
package smile.validation;

import java.util.Arrays;
import smile.math.MathEx;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Nejc Ilenic
 */
public class GroupKFoldTest {

    @Test
    public void testNoGroupsInSameFold() {
        int k = 3;
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};

        Bag[] bags = CrossValidation.nonoverlap(groups, k);

        for (int i = 0; i < k; i++) {
            int[] train = MathEx.unique(Arrays.stream(bags[i].samples()).map(x -> groups[x]).toArray());
            int[] test = MathEx.unique(Arrays.stream(bags[i].oob()).map(x -> groups[x]).toArray());

            boolean anyTrainGroupInTestFold = Arrays.stream(train)
                    .anyMatch(trainGroup -> Arrays.stream(test).anyMatch(testGroup -> trainGroup == testGroup));

            assertFalse(anyTrainGroupInTestFold);
        }
    }

    @Test
    public void testInvalidKParameter() {
        assertThrows(IllegalArgumentException.class, () -> {
            int[] groups = new int[]{1, 2, 2, 0, 0, 0, 2, 1, 1, 2};
            CrossValidation.nonoverlap(groups, -1);
        });
    }

    @Test
    public void testInvalidGroupsKParameters() {
        assertThrows(IllegalArgumentException.class, () -> {
            int[] groups = new int[]{1, 2, 2, 0, 0, 0, 2, 1, 1, 2};
            CrossValidation.nonoverlap(groups, 4);
        });
    }
}
