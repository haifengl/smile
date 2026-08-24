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
package smile.llm.transformer;

import smile.deep.tensor.Tensor;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for shared transformer building blocks ({@link FeedForward}).
 *
 * @author Haifeng Li
 */
public class FeedForwardTest {

    @Test
    public void testGivenFeedForwardWithNoMultiplierWhenCreatedThenForwardOutputDimMatchesInput() {
        // hiddenDim = 4*64=256 → 2/3: 170 → round up to multiple of 256 → 256
        FeedForward ff = new FeedForward(64, 4 * 64, 256, null);
        Tensor x = Tensor.ones(1, 2, 64);
        Tensor out = ff.forward(x);
        assertEquals(64, out.shape()[2], "Output last dim should equal input dim");
        x.close(); out.close();
    }

    @Test
    public void testGivenFeedForwardWithMultiplierWhenCreatedThenForwardOutputDimMatchesInput() {
        FeedForward ff = new FeedForward(64, 4 * 64, 256, 1.3);
        Tensor x = Tensor.ones(1, 2, 64);
        Tensor out = ff.forward(x);
        assertEquals(64, out.shape()[2]);
        x.close(); out.close();
    }

    @Test
    public void testGivenFeedForwardWhenForwardCalledThenOutputShapeMatchesInput() {
        FeedForward ff = new FeedForward(64, 4 * 64, 256, null);
        Tensor x = Tensor.ones(1, 4, 64);
        Tensor out = ff.forward(x);
        assertArrayEquals(new long[]{1, 4, 64}, out.shape());
        x.close(); out.close();
    }
}
