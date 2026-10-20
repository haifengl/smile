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
package smile.llm.model.minimax;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Opt-in check of {@link MiniMaxTokenizer} against the Hugging Face tokenizer on the real
 * {@code tokenizer.json}. Set {@code MINIMAX_CHECKPOINT_DIR} to a directory containing it.
 *
 * @author Haifeng Li
 */
public class MiniMaxTokenizerRealTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static int[] ids(JsonNode node) {
        int[] out = new int[node.size()];
        for (int i = 0; i < out.length; i++) out[i] = node.get(i).asInt();
        return out;
    }

    private static MiniMaxTokenizer tokenizer() throws IOException {
        String dir = System.getenv("MINIMAX_CHECKPOINT_DIR");
        assumeTrue(dir != null && new File(dir, "tokenizer.json").exists(), "MINIMAX_CHECKPOINT_DIR not set");
        return MiniMaxTokenizer.of(dir);
    }

    @Test
    public void testGivenPlainTextWhenEncodedThenIdsEqualHuggingFace() throws IOException {
        MiniMaxTokenizer tok = tokenizer();
        for (JsonNode c : MAPPER.readTree(new File("deep/src/test/resources/minimax/tokenizer_goldens.json"))) {
            String text = c.get("text").asString();
            int[] expected = ids(c.get("ids"));
            assertArrayEquals(expected, tok.encodeWithSpecials(text), "encode: " + text);
            assertEquals(c.get("decoded").asString(), tok.tryDecode(expected, false), "decode: " + text);
        }
    }

    @Test
    public void testGivenRenderedChatPromptsWhenEncodedThenIdsEqualHuggingFace() throws IOException {
        MiniMaxTokenizer tok = tokenizer();
        for (JsonNode c : MAPPER.readTree(new File("deep/src/test/resources/minimax/chat_template_goldens.json"))) {
            int[] actual = tok.encodeWithSpecials(c.get("expected").asString());
            int[] expected = ids(c.get("ids"));
            assertArrayEquals(expected, actual, "case " + c.get("name").asString() + " first mismatch at "
                    + Arrays.mismatch(expected, actual));
        }
    }

    @Test
    public void testGivenGeneratedMarkersWhenDecodedForDisplayThenControlHiddenAndToolMarkerKept() throws IOException {
        MiniMaxTokenizer tok = tokenizer();
        int[] ids = tok.encodeWithSpecials("<mm:think>why</mm:think>ok]<]minimax[>[<tool_call>x]<]minimax[>[</tool_call>[e~[");
        assertEquals("<think>why</think>ok]<]minimax[>[<tool_call>x]<]minimax[>[</tool_call>",
                tok.tryDecode(ids, true));
        assertEquals(1, tok.encodeWithSpecials("[e~[").length);
        assertEquals(tok.stopTokens()[0], tok.encodeWithSpecials("[e~[")[0]);
    }
}
