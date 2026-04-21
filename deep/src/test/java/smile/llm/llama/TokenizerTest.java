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
package smile.llm.llama;

import java.io.IOException;
import smile.llm.Message;
import smile.llm.Role;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Llama 3 {@link Tokenizer}.
 *
 * @author Haifeng Li
 */
public class TokenizerTest {

    private static final String MODEL = "deep/src/test/resources/model/llama_tokenizer_v3.model";
    private static Tokenizer tokenizer;

    @BeforeAll
    public static void loadTokenizer() throws IOException {
        tokenizer = Tokenizer.of(MODEL);
    }

    // -----------------------------------------------------------------------
    // tokenize — with special-token mode enabled
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSpecialTokensAllowedWhenTokenizingThenSpecialTokensRecognized() {
        tokenizer.allowSpecialTokens(true);
        String[] expected = { "<|begin_of_text|>", "This", " is", " a", " test", " sentence", ".", "<|end_of_text|>" };
        assertArrayEquals(expected,
                tokenizer.tokenize("<|begin_of_text|>This is a test sentence.<|end_of_text|>"));
    }

    @Test
    public void testGivenSpecialTokensDisallowedWhenTokenizingThenSpecialTokensSplit() {
        tokenizer.allowSpecialTokens(false);
        String[] expected = { "<|", "begin", "_of", "_text", "|>", "This",
                " is", " a", " test", " sentence", ".<|", "end", "_of", "_text", "|>" };
        assertArrayEquals(expected,
                tokenizer.tokenize("<|begin_of_text|>This is a test sentence.<|end_of_text|>"));
    }

    // -----------------------------------------------------------------------
    // encode / decode roundtrip
    // -----------------------------------------------------------------------

    @Test
    public void testGivenEncodeWithBosAndEosThenBothSpecialTokensPresent() {
        int[] expected = { 128000, 2028, 374, 264, 1296, 11914, 13, 128001 };
        assertArrayEquals(expected, tokenizer.encode("This is a test sentence.", true, true));
    }

    @Test
    public void testGivenEncodeWithEosOnlyThenBosAbsent() {
        int[] expected = { 2028, 374, 264, 1296, 11914, 13, 128001 };
        assertArrayEquals(expected, tokenizer.encode("This is a test sentence.", false, true));
    }

    @Test
    public void testGivenEncodeWithBosOnlyThenEosAbsent() {
        int[] expected = { 128000, 2028, 374, 264, 1296, 11914, 13 };
        assertArrayEquals(expected, tokenizer.encode("This is a test sentence.", true, false));
    }

    @Test
    public void testGivenEncodeWithNeitherBosNorEosThenPlainTokensOnly() {
        int[] expected = { 2028, 374, 264, 1296, 11914, 13 };
        assertArrayEquals(expected, tokenizer.encode("This is a test sentence.", false, false));
    }

    @Test
    public void testGivenDecodeWhenTokensIncludeBosAndEosThenFullStringReturned() {
        int[] tokens = { 128000, 2028, 374, 264, 1296, 11914, 13, 128001 };
        assertEquals("<|begin_of_text|>This is a test sentence.<|end_of_text|>",
                tokenizer.decode(tokens));
    }

    // -----------------------------------------------------------------------
    // encodeMessage
    // -----------------------------------------------------------------------

    @Test
    public void testGivenUserMessageWhenEncodedThenContainsHeaderAndEotTokens() {
        int[] expected = {
                128006,  // <|start_header_id|>
                882,     // "user"
                128007,  // <|end_of_header|>
                271,     // "\n\n"
                2028, 374, 264, 1296, 11914, 13,  // "This is a test sentence."
                128009   // <|eot_id|>
        };
        assertArrayEquals(expected,
                tokenizer.encodeMessage(new Message(Role.user, "This is a test sentence.")));
    }

    // -----------------------------------------------------------------------
    // encodeDialog
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSystemAndUserMessagesWhenDialogEncodedThenStructureIsCorrect() {
        int[] expected = {
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
                2028, 374, 264, 2077, 13,  // "This is a response."
                128009,  // <|eot_id|>
                128006,  // <|start_header_id|>
                78191,   // "assistant"
                128007,  // <|end_of_header|>
                271      // "\n\n"
        };
        assertArrayEquals(expected, tokenizer.encodeDialog(
                new Message(Role.system, "This is a test sentence."),
                new Message(Role.user,   "This is a response.")
        ));
    }

    @Test
    public void testGivenSingleUserMessageWhenDialogEncodedThenMatchesKnownTokens() {
        int[] expected = {
                128000, 128006, 882, 128007, 271,
                12840, 374, 279, 11363, 315, 1253, 13767, 1082, 30,
                128009, 128006, 78191, 128007, 271
        };
        assertArrayEquals(expected, tokenizer.encodeDialog(
                new Message(Role.user, "what is the recipe of mayonnaise?")
        ));
    }

    // -----------------------------------------------------------------------
    // stopTokens / pad
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTokenizerWhenCheckingStopTokensThenContainsEotAndEndOfText() {
        int[] stop = tokenizer.stopTokens();
        assertEquals(2, stop.length);
        // <|end_of_text|> and <|eot_id|>
        assertEquals(128001, stop[0]);  // <|end_of_text|>
        assertEquals(128009, stop[1]);  // <|eot_id|>
    }

    @Test
    public void testGivenTokenizerWhenCheckingPadThenReturnsMinusOne() {
        assertEquals(-1, tokenizer.pad());
    }

    // -----------------------------------------------------------------------
    // encode/decode roundtrip for numeric tokens
    // -----------------------------------------------------------------------

    @Test
    public void testGivenNumericTextWhenEncodedThenDecodedBackToOriginal() {
        String text = "12345";
        int[] tokens = tokenizer.encode(text, false, false);
        assertEquals(text, tokenizer.decode(tokens));
    }

    // -----------------------------------------------------------------------
    // Message factory methods (since we added them)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenMessageFactoryMethodsWhenCalledThenCorrectRoleAssigned() {
        assertEquals(Role.system,    Message.system("hello").role());
        assertEquals(Role.user,      Message.user("hello").role());
        assertEquals(Role.assistant, Message.assistant("hello").role());
    }
}

