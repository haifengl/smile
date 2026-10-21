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
package smile.llm.model.qwen;

import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import smile.llm.ChatOptions;
import smile.llm.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in (needs {@code QWEN_DIR}, a Qwen3.8 checkpoint directory): token ids of rendered prompts equal the HF
 * tokenizer's, including control tokens typed inside user text and the tool markers that are added tokens.
 *
 * @author Haifeng Li
 */
public class QwenTokenizerRealTest {
    private static Tokenizer tokenizer() throws Exception {
        String dir = System.getenv("QWEN_DIR");
        assumeTrue(dir != null, "QWEN_DIR not set");
        return Tokenizer.of(dir);
    }

    @Test
    public void testGivenRenderedPromptsWhenEncodedThenIdsEqualHuggingFace() throws Exception {
        Tokenizer tokenizer = tokenizer();
        JsonNode goldens;
        try (InputStream in = getClass().getResourceAsStream("/qwen/tokenizer_goldens.json")) {
            goldens = new ObjectMapper().readTree(in);
        }
        for (JsonNode g : goldens) {
            int[] expected = new int[g.get("ids").size()];
            for (int i = 0; i < expected.length; i++) {
                expected[i] = g.get("ids").get(i).asInt();
            }
            String text = g.get("text").asString();
            assertArrayEquals(expected, tokenizer.encodeWithSpecials(text),
                    "text: " + text.substring(0, Math.min(60, text.length())).replace("\n", "\\n"));
        }
    }

    @Test
    public void testGivenCheckpointWhenOpenedThenTemplateVariantIsDetected() throws Exception {
        Tokenizer tokenizer = tokenizer();
        assertNotNull(tokenizer.chatTemplate());
        // a plain question now carries the reasoning-effort system turn and an opened think block
        String prompt = tokenizer.renderDialog(new Message[]{Message.user("hi")}, null);
        if (tokenizer.chatTemplate().reasoningEffort()) {
            assertTrue(prompt.startsWith("<|im_start|>system\nReasoning effort is set to xhigh."), prompt);
        }
        assertTrue(prompt.endsWith("<|im_start|>assistant\n<think>\n"), prompt);
        int[] ids = tokenizer.encodeDialog(new Message[]{Message.user("hi")}, null);
        assertEquals(ids[ids.length - 2], tokenizer.encodeWithSpecials("<think>")[0]);
        ChatOptions off = ChatOptions.NONE.withThinkingMode(ChatOptions.THINKING_DISABLED);
        assertTrue(tokenizer.renderDialog(new Message[]{Message.user("hi")}, off).endsWith("</think>\n\n"));
        assertNotEquals(Arrays.toString(ids),
                Arrays.toString(tokenizer.encodeDialog(new Message[]{Message.user("hi")}, off)));
    }
}
