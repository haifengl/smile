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
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BagTest {

    @Test
    public void givenNAndHoldout_whenSplit_thenEveryIndexAppearsExactlyOnce() {
        int n = 100;
        double holdout = 0.2;
        Bag bag = Bag.split(n, holdout);

        boolean[] seen = new boolean[n];
        for (int i : bag.samples()) {
            assertFalse(seen[i], "Duplicate index in train: " + i);
            seen[i] = true;
        }
        for (int i : bag.oob()) {
            assertFalse(seen[i], "Index " + i + " appears in both train and test");
            seen[i] = true;
        }
        for (int j = 0; j < n; j++) {
            assertTrue(seen[j], "Index " + j + " missing from split");
        }
    }

    @Test
    public void givenNAndHoldout_whenSplit_thenTestSizeIsApproximatelyHoldoutFraction() {
        int n = 200;
        double holdout = 0.3;
        Bag bag = Bag.split(n, holdout);

        // Math.round(200 * 0.7) = 140 train, 60 test
        int expectedTest = n - (int) Math.round(n * (1 - holdout));
        assertEquals(expectedTest, bag.oob().length);
        assertEquals(n - expectedTest, bag.samples().length);
    }

    @Test
    public void givenInvalidN_whenSplit_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Bag.split(-1, 0.2));
    }

    @Test
    public void givenInvalidHoldout_whenSplit_thenThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> Bag.split(10, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Bag.split(10, 1.0));
        assertThrows(IllegalArgumentException.class, () -> Bag.split(10, -0.1));
        assertThrows(IllegalArgumentException.class, () -> Bag.split(10, 1.1));
    }

    @Test
    public void givenCategoryLabels_whenStratify_thenEveryIndexAppearsExactlyOnce() {
        int n = 90;
        int[] category = new int[n];
        // Three balanced classes of 30 each
        for (int i = 0; i < n; i++) category[i] = i / 30;

        Bag bag = Bag.stratify(category, 0.2);

        boolean[] seen = new boolean[n];
        for (int i : bag.samples()) {
            assertFalse(seen[i], "Duplicate index in train: " + i);
            seen[i] = true;
        }
        for (int i : bag.oob()) {
            assertFalse(seen[i], "Index " + i + " in both splits");
            seen[i] = true;
        }
        for (int j = 0; j < n; j++) {
            assertTrue(seen[j], "Index " + j + " missing from stratified split");
        }
    }

    @Test
    public void givenCategoryLabels_whenStratify_thenBothSplitsContainAllClasses() {
        int n = 90;
        int[] category = new int[n];
        for (int i = 0; i < n; i++) category[i] = i / 30;

        Bag bag = Bag.stratify(category, 0.3);

        int[] trainClasses = MathEx.unique(Arrays.stream(bag.samples()).map(i -> category[i]).toArray());
        int[] testClasses  = MathEx.unique(Arrays.stream(bag.oob()).map(i -> category[i]).toArray());

        Arrays.sort(trainClasses);
        Arrays.sort(testClasses);
        assertArrayEquals(new int[]{0, 1, 2}, trainClasses);
        assertArrayEquals(new int[]{0, 1, 2}, testClasses);
    }

    @Test
    public void givenInvalidHoldout_whenStratify_thenThrowIllegalArgumentException() {
        int[] category = {0, 1, 0, 1};
        assertThrows(IllegalArgumentException.class, () -> Bag.stratify(category, 0.0));
        assertThrows(IllegalArgumentException.class, () -> Bag.stratify(category, 1.0));
    }
}
