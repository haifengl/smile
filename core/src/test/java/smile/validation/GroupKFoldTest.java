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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for grouped cross-validation, where samples that share a group label
 * must never be split across the training and testing parts of a fold.
 *
 * @author Haifeng Li
 */
public class GroupKFoldTest {
    /**
     * Group labels of ten samples drawn from three distinct groups.
     */
    private static final int[] GROUP = {2, 2, 0, 0, 0, 1, 1, 2, 1, 2};

    @Test
    public void givenGroupedSamples_whenSplitting_thenNoGroupSpansTrainAndTest() {
        // Given ten samples that belong to three distinct groups.
        int k = 3;

        // When splitting the samples into k folds.
        Bag[] bags = CrossValidation.nonoverlap(GROUP, k);

        // Then each group is confined to a single fold, i.e. the group labels
        // observed in the training part and in the testing part of a fold are
        // disjoint.
        assertEquals(k, bags.length);
        for (Bag bag : bags) {
            Set<Integer> trainGroups = groupLabels(bag.samples());
            Set<Integer> testGroups = groupLabels(bag.oob());
            assertTrue(Collections.disjoint(trainGroups, testGroups),
                    "A group appears in both the training and testing part of a fold.");
        }
    }

    @Test
    public void givenNonPositiveK_whenSplitting_thenThrowIllegalArgumentException() {
        // Given a non-positive number of folds.
        // When splitting the samples.
        // Then an IllegalArgumentException is thrown.
        assertThrows(IllegalArgumentException.class, () -> CrossValidation.nonoverlap(GROUP, -1));
    }

    @Test
    public void givenMoreFoldsThanGroups_whenSplitting_thenThrowIllegalArgumentException() {
        // Given more folds than there are distinct groups.
        // When splitting the samples.
        // Then an IllegalArgumentException is thrown.
        assertThrows(IllegalArgumentException.class, () -> CrossValidation.nonoverlap(GROUP, 4));
    }

    /**
     * Returns the distinct group labels of the given sample indices.
     * @param samples the sample indices.
     * @return the distinct group labels.
     */
    private static Set<Integer> groupLabels(int[] samples) {
        Set<Integer> labels = new HashSet<>();
        for (int sample : samples) {
            labels.add(GROUP[sample]);
        }
        return labels;
    }
}
