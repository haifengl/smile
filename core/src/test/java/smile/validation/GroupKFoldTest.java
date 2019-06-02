package smile.validation;

import org.junit.Test;
import smile.math.Math;

import java.util.Arrays;

import static org.junit.Assert.assertFalse;

public class GroupKFoldTest {

    @Test
    public void testNoGroupsInSameFold() {
        int n = 10;
        int k = 3;
        int[] groups = new int[] {1, 2, 2, 0, 0, 0, 2, 1, 1, 2};

        GroupKFold split = new GroupKFold(n, k, groups);

        for (int i = 0; i < k; i++) {
            int[] trainGroups = Math.unique(Arrays.stream(split.train[i]).map(x -> groups[x]).toArray());
            int[] testGroups = Math.unique(Arrays.stream(split.test[i]).map(x -> groups[x]).toArray());

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
