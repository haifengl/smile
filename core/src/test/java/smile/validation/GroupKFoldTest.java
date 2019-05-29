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
