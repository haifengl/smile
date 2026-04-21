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
package smile.llm;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FinishReason} enum.
 *
 * @author Haifeng Li
 */
public class FinishReasonTest {
    @Test
    public void testGivenFinishReasonWhenAllValuesEnumeratedThenFourExist() {
        FinishReason[] values = FinishReason.values();
        assertEquals(4, values.length);
    }

    @Test
    public void testGivenFinishReasonStopWhenToStringThenIsStop() {
        assertEquals("stop", FinishReason.stop.name());
    }

    @Test
    public void testGivenFinishReasonLengthWhenValueOfThenMatchesEnum() {
        assertEquals(FinishReason.length, FinishReason.valueOf("length"));
    }

    @Test
    public void testGivenFinishReasonFunctionCallWhenNameCalledThenCorrect() {
        assertEquals("function_call", FinishReason.function_call.name());
    }

    @Test
    public void testGivenFinishReasonContentFilterWhenNameCalledThenCorrect() {
        assertEquals("content_filter", FinishReason.content_filter.name());
    }
}
