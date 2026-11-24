/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * SMILE is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SMILE is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.inkling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link InklingShard} arithmetic (no GPU).
 *
 * @author Haifeng Li
 */
public class InklingShardTest {
    @Test
    public void testGivenSingleShardWhenPartitionedThenItOwnsEverything() {
        InklingShard s = InklingShard.SINGLE;
        assertEquals(48, s.part(48));
        assertEquals(0, s.start(48));
        s.allReduce(null);                 // a no-op for one rank, no tensor needed
    }

    @Test
    public void testGivenInvalidRankOrMissingGroupWhenCreatedThenItIsRefused() {
        // more than one rank needs a communicator; the rank must lie inside the group
        assertThrows(IllegalArgumentException.class, () -> new InklingShard(0, 8, null));
        assertThrows(IllegalArgumentException.class, () -> new InklingShard(8, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new InklingShard(-1, 1, null));
        assertThrows(IllegalArgumentException.class, () -> new InklingShard(0, 0, null));
    }
}
