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
package smile.llm.engine;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for greedy speculative accept/reject.
 *
 * @author Haifeng Li
 */
public class SpeculativeDecodingTest {

    @Test
    public void testGivenFullMatchWhenAcceptGreedyThenAllDraftsPlusBonus() {
        int[] drafts = {10, 20, 30};
        int[] targets = {10, 20, 30, 99};
        var result = SpeculativeDecoding.acceptGreedy(drafts, targets);
        assertEquals(3, result.numDraftAccepted());
        assertArrayEquals(new int[]{10, 20, 30, 99}, result.acceptedTokens());
        assertEquals(99, result.bonusToken());
        assertEquals(4, result.numTokens());
    }

    @Test
    public void testGivenFirstMismatchWhenAcceptGreedyThenOnlyBonus() {
        int[] drafts = {10, 20, 30};
        int[] targets = {11, 20, 30, 99};
        var result = SpeculativeDecoding.acceptGreedy(drafts, targets);
        assertEquals(0, result.numDraftAccepted());
        assertArrayEquals(new int[]{11}, result.acceptedTokens());
        assertEquals(11, result.bonusToken());
    }

    @Test
    public void testGivenMidMismatchWhenAcceptGreedyThenPrefixPlusBonus() {
        int[] drafts = {10, 20, 30};
        int[] targets = {10, 21, 30, 99};
        var result = SpeculativeDecoding.acceptGreedy(drafts, targets);
        assertEquals(1, result.numDraftAccepted());
        assertArrayEquals(new int[]{10, 21}, result.acceptedTokens());
    }

    @Test
    public void testGivenBadLengthsWhenAcceptGreedyThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SpeculativeDecoding.acceptGreedy(new int[]{1}, new int[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> SpeculativeDecoding.acceptGreedy(new int[0], new int[]{1}));
    }
}
