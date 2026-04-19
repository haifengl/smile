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
package smile.vq;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NeighborhoodTest {
    @Test
    void givenBubbleNeighborhood_whenInsideAndOutsideRadius_thenExpectedValues() {
        // Given
        Neighborhood neighborhood = Neighborhood.bubble(2);

        // When
        double winner = neighborhood.of(0, 0, 0);
        double inside = neighborhood.of(1, -1, 10);
        double boundary = neighborhood.of(2, 0, 10);
        double outside = neighborhood.of(3, 0, 10);

        // Then
        assertEquals(1.0, winner);
        assertEquals(1.0, inside);
        assertEquals(0.0, boundary);
        assertEquals(0.0, outside);
    }

    @Test
    void givenGaussianNeighborhood_whenTimeIncreases_thenResponseDecaysAwayFromWinner() {
        // Given
        Neighborhood neighborhood = Neighborhood.Gaussian(2.0, 100.0);

        // When
        double winnerAtStart = neighborhood.of(0, 0, 0);
        double nearAtStart = neighborhood.of(1, 0, 0);
        double nearLater = neighborhood.of(1, 0, 50);

        // Then
        assertEquals(1.0, winnerAtStart, 1E-12);
        assertTrue(nearAtStart > nearLater);
        assertTrue(nearLater > 0.0);
    }
}

