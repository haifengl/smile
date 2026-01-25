/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.llama;

import java.io.IOException;
import smile.llm.Message;
import smile.llm.Role;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 *
 * @author Haifeng Li
 */
public class TokenizerTest {

    public TokenizerTest() {
    }

    @BeforeAll
    public static void setUpClass() throws Exception {
    }

    @AfterAll
    public static void tearDownClass() throws Exception {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    public void test() throws IOException {
        var tokenizer = Tokenizer.of("deep/src/test/resources/model/llama_tokenizer_v3.model");

        tokenizer.allowSpecialTokens(true);
        String[] tokens = { "<|begin_of_text|>", "This", " is", " a", " test", " sentence", ".", "<|end_of_text|>" };
        assertArrayEquals(tokens, tokenizer.tokenize("<|begin_of_text|>This is a test sentence.<|end_of_text|>"));

        tokenizer.allowSpecialTokens(false);
        String[] noSpecialTokens = { "<|", "begin", "_of", "_text", "|>", "This",
                " is", " a", " test", " sentence", ".<|", "end", "_of", "_text", "|>" };
        assertArrayEquals(noSpecialTokens, tokenizer.tokenize("<|begin_of_text|>This is a test sentence.<|end_of_text|>"));

        int[] tokens1 = { 128000, 2028, 374, 264, 1296, 11914, 13, 128001 };
        assertEquals("<|begin_of_text|>This is a test sentence.<|end_of_text|>", tokenizer.decode(tokens1));
        assertArrayEquals(tokens1, tokenizer.encode("This is a test sentence.", true, true));

        int[] tokens2 = { 2028, 374, 264, 1296, 11914, 13, 128001 };
        assertEquals("This is a test sentence.<|end_of_text|>", tokenizer.decode(tokens2));
        assertArrayEquals(tokens2, tokenizer.encode("This is a test sentence.", false, true));

        int[] tokens3 = { 128000, 2028, 374, 264, 1296, 11914, 13 };
        assertEquals("<|begin_of_text|>This is a test sentence.", tokenizer.decode(tokens3));
        assertArrayEquals(tokens3, tokenizer.encode("This is a test sentence.", true, false));

        int[] tokens4 = { 2028, 374, 264, 1296, 11914, 13 };
        assertEquals("This is a test sentence.", tokenizer.decode(tokens4));
        assertArrayEquals(tokens4, tokenizer.encode("This is a test sentence.", false, false));

        int[] messageTokens = {
                128006,  // <|start_header_id|>
                882,     // "user"
                128007,  // <|end_of_header|>
                271,     // "\n\n"
                2028, 374, 264, 1296, 11914, 13,  // This is a test sentence.
                128009   // <|eot_id|>
        };

        Message message = new Message(Role.user, "This is a test sentence.");
        assertArrayEquals(messageTokens, tokenizer.encodeMessage(message));

        int[] dialogTokens = {
                128000,  // <|begin_of_text|>
                128006,  // <|start_header_id|>
                9125,    // "system"
                128007,  // <|end_of_header|>
                271,     // "\n\n"
                2028, 374, 264, 1296, 11914, 13,  // "This is a test sentence."
                128009,  // <|eot_id|>
                128006,  // <|start_header_id|>
                882,     // "user"
                128007,  // <|end_of_header|>
                271,     // "\n\n"
                2028, 374, 264, 2077, 13,  // "This is a response.",
                128009,  // <|eot_id|>
                128006,  // <|start_header_id|>
                78191,   // "assistant"
                128007,  // <|end_of_header|>
                271      // "\n\n"
        };
        assertArrayEquals(dialogTokens, tokenizer.encodeDialog(
                new Message(Role.system, "This is a test sentence."),
                new Message(Role.user, "This is a response.")
        ));

        int[] dialogTokens2 = {
                128000, 128006,   882, 128007,    271,  12840,    374,    279,  11363,
                315,    1253,   13767,   1082,     30, 128009, 128006,  78191, 128007,
                271};
        assertArrayEquals(dialogTokens2, tokenizer.encodeDialog(
                new Message(Role.user, "what is the recipe of mayonnaise?")
        ));
    }
}
