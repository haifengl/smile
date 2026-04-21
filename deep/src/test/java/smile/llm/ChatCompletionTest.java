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
 * Unit tests for {@link ChatCompletion} record.
 *
 * @author Haifeng Li
 */
public class ChatCompletionTest {
    @Test
    public void testGivenChatCompletionWhenCreatedThenAllAccessorsReturnCorrectValues() {
        int[] prompt = {1, 2, 3};
        int[] completion = {4, 5};
        float[] logprobs = {-0.5f, -1.2f};
        ChatCompletion cc = new ChatCompletion("llama3", "Hi!", prompt, completion, FinishReason.stop, logprobs);
        assertEquals("llama3", cc.model());
        assertEquals("Hi!", cc.content());
        assertArrayEquals(prompt, cc.promptTokens());
        assertArrayEquals(completion, cc.completionTokens());
        assertEquals(FinishReason.stop, cc.reason());
        assertArrayEquals(logprobs, cc.logprobs(), 1e-7f);
    }

    @Test
    public void testGivenChatCompletionWithNullLogprobsWhenCreatedThenAllowed() {
        ChatCompletion cc = new ChatCompletion("model", "text", new int[]{1}, new int[]{2}, FinishReason.length, null);
        assertNull(cc.logprobs());
    }

    @Test
    public void testGivenChatCompletionWithNullModelWhenCreatedThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChatCompletion(null, "text", new int[]{1}, new int[]{2}, FinishReason.stop, null));
    }

    @Test
    public void testGivenChatCompletionWithNullContentWhenCreatedThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChatCompletion("model", null, new int[]{1}, new int[]{2}, FinishReason.stop, null));
    }

    @Test
    public void testGivenChatCompletionWithNullReasonWhenCreatedThenThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChatCompletion("model", "text", new int[]{1}, new int[]{2}, null, null));
    }

    @Test
    public void testGivenTwoChatCompletionsWithSameFieldsWhenComparedThenAreEqual() {
        int[] p = {1};
        int[] c = {2};
        ChatCompletion a = new ChatCompletion("m", "t", p, c, FinishReason.stop, null);
        ChatCompletion b = new ChatCompletion("m", "t", p, c, FinishReason.stop, null);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testGivenChatCompletionWithFinishReasonLengthWhenQueriedThenReasonIsLength() {
        ChatCompletion cc = new ChatCompletion("gpt", "...", new int[0], new int[0], FinishReason.length, null);
        assertEquals(FinishReason.length, cc.reason());
    }
}



