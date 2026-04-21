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
package smile.llm.tokenizer;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import smile.util.Bytes;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Tiktoken} — the base BPE tokenizer class in the
 * {@code smile.llm.tokenizer} package.
 *
 * <p>Tests are structured using Given/When/Then and run entirely on a small
 * in-memory vocabulary (printable ASCII byte-pairs) so they execute in
 * milliseconds with no external resources required, except for the
 * except for the {@link #testGivenValidTiktokenFileWhenLoadedThenMapIsPopulated} test which reads
 * the test resource file.
 *
 * @author Haifeng Li
 */
public class TiktokenTest {

    /**
     * Minimal vocabulary:
     * <ul>
     *   <li>Ranks 0–94: single printable ASCII bytes (0x20=' ' … 0x7e='~').</li>
     *   <li>Ranks 95–99: bigrams "he", "lo", " w", "or", "ld".</li>
     * </ul>
     * Special tokens: rank 100 = {@code <bos>}, rank 101 = {@code <eos>}.
     */
    private static Tiktoken tokenizer;

    /** Regex that matches sequences of non-whitespace or whitespace. */
    private static final Pattern REGEX = Pattern.compile("[^\\s]+|\\s+");

    @BeforeAll
    public static void buildTokenizer() {
        // Single-byte ranks: ' '(32)=0, '!'(33)=1, … '~'(126)=94
        Map<Bytes, Integer> ranks = new HashMap<>();
        for (int c = 32; c <= 126; c++) {
            ranks.put(new Bytes(new byte[]{(byte) c}), c - 32);
        }
        // Bigrams: "he"=95, "lo"=96, " w"=97, "or"=98, "ld"=99
        ranks.put(bytes("he"), 95);
        ranks.put(bytes("lo"), 96);
        ranks.put(bytes(" w"), 97);
        ranks.put(bytes("or"), 98);
        ranks.put(bytes("ld"), 99);

        tokenizer = new Tiktoken(REGEX, ranks, "<bos>", "<eos>", "<bos>", "<eos>", "<sep>");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Bytes bytes(String s) {
        return new Bytes(s.getBytes(StandardCharsets.UTF_8));
    }

    // -----------------------------------------------------------------------
    // size()
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTokenizerWhenSizeQueriedThenEqualsVocabPlusSpecialTokens() {
        // 95 regular tokens (ranks 0..94) + 5 bigrams (95..99) + 3 special ("<bos>","<eos>","<sep>")
        assertEquals(103, tokenizer.size());
    }

    // -----------------------------------------------------------------------
    // allowSpecialTokens / isSpecialTokenAllowed
    // -----------------------------------------------------------------------

    @Test
    public void testGivenDefaultStateWhenIsSpecialTokenAllowedThenFalse() {
        // Default should be false (special tokens encoded as plain text)
        assertFalse(tokenizer.isSpecialTokenAllowed());
    }

    @Test
    public void testGivenAllowSpecialTrueWhenIsSpecialTokenAllowedThenTrue() {
        tokenizer.allowSpecialTokens(true);
        assertTrue(tokenizer.isSpecialTokenAllowed());
        tokenizer.allowSpecialTokens(false); // restore
    }

    // -----------------------------------------------------------------------
    // specialToken()
    // -----------------------------------------------------------------------

    @Test
    public void testGivenKnownSpecialTokenWhenQueriedThenReturnsCorrectId() {
        Integer id = tokenizer.specialToken("<bos>");
        assertNotNull(id);
        assertEquals(100, id); // 100 tokens in vocab → bos = index 100
    }

    @Test
    public void testGivenUnknownSpecialTokenWhenQueriedThenReturnsNull() {
        assertNull(tokenizer.specialToken("<unknown>"),
                "specialToken() must return null for unregistered tokens");
    }

    // -----------------------------------------------------------------------
    // Constructor validation — BOS/EOS not in specialTokens
    // -----------------------------------------------------------------------

    @Test
    public void testGivenBosNotInSpecialTokensWhenConstructingThenThrowsIllegalArgument() {
        Map<Bytes, Integer> ranks = new HashMap<>();
        ranks.put(bytes("a"), 0);
        assertThrows(IllegalArgumentException.class, () ->
                new Tiktoken(REGEX, ranks, "<missing_bos>", "<eos>", "<eos>"),
                "Constructor must throw when bos is not in specialTokens");
    }

    @Test
    public void testGivenEosNotInSpecialTokensWhenConstructingThenThrowsIllegalArgument() {
        Map<Bytes, Integer> ranks = new HashMap<>();
        ranks.put(bytes("a"), 0);
        assertThrows(IllegalArgumentException.class, () ->
                new Tiktoken(REGEX, ranks, "<bos>", "<missing_eos>", "<bos>"),
                "Constructor must throw when eos is not in specialTokens");
    }

    // -----------------------------------------------------------------------
    // tokenize — without special tokens
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSimpleTextWhenTokenizedWithoutSpecialTokensThenWordsAreSegmented() {
        tokenizer.allowSpecialTokens(false);
        String[] tokens = tokenizer.tokenize("hello world");
        // REGEX splits: "hello" (non-whitespace), " " (whitespace), "world" (non-whitespace)
        assertArrayEquals(new String[]{"hello", " ", "world"}, tokens);
    }

    @Test
    public void testGivenEmptyStringWhenTokenizedThenReturnsEmptyArray() {
        String[] tokens = tokenizer.tokenize("");
        assertEquals(0, tokens.length);
    }

    @Test
    public void testGivenSpecialTokenInTextWhenSpecialDisabledThenTreatedAsPlainText() {
        tokenizer.allowSpecialTokens(false);
        // <bos> should not be a single segment; it's tokenized by the main regex
        String[] tokens = tokenizer.tokenize("<bos>");
        // REGEX matches non-whitespace sequences: "<bos>"
        assertArrayEquals(new String[]{"<bos>"}, tokens);
    }

    // -----------------------------------------------------------------------
    // tokenize — with special tokens
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSpecialTokenInTextWhenSpecialEnabledThenIsolatedAsToken() {
        tokenizer.allowSpecialTokens(true);
        String[] tokens = tokenizer.tokenize("<bos>hello");
        // specialTokenPattern splits on <bos>: ["<bos>", "hello"]
        assertArrayEquals(new String[]{"<bos>", "hello"}, tokens);
        tokenizer.allowSpecialTokens(false); // restore
    }

    @Test
    public void testGivenTextWithMultipleSpecialTokensWhenTokenizedThenAllIsolated() {
        tokenizer.allowSpecialTokens(true);
        String[] tokens = tokenizer.tokenize("<bos>hi<eos>");
        assertArrayEquals(new String[]{"<bos>", "hi", "<eos>"}, tokens);
        tokenizer.allowSpecialTokens(false); // restore
    }

    // -----------------------------------------------------------------------
    // encode — without BOS/EOS
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSingleCharWhenEncodedThenReturnsSingleToken() {
        int[] tokens = tokenizer.encode("h");
        // 'h' = ASCII 104, rank = 104-32 = 72
        assertArrayEquals(new int[]{72}, tokens);
    }

    @Test
    public void testGivenBigramTextWhenEncodedThenMergedToken() {
        // "he" should be merged to rank 95 (bigram in vocab)
        int[] tokens = tokenizer.encode("he");
        assertArrayEquals(new int[]{95}, tokens);
    }

    @Test
    public void testGivenEmptyStringWhenEncodedThenReturnsEmptyArray() {
        int[] tokens = tokenizer.encode("", false, false);
        assertEquals(0, tokens.length);
    }

    @Test
    public void testGivenEncodeWithBosAndEosThenBothPresent() {
        int[] tokens = tokenizer.encode("hi", true, true);
        // First = bos (100), last = eos (101)
        assertEquals(100, tokens[0], "first token must be BOS");
        assertEquals(101, tokens[tokens.length - 1], "last token must be EOS");
    }

    @Test
    public void testGivenEncodeWithBosOnlyThenEosAbsent() {
        int[] tokens = tokenizer.encode("hi", true, false);
        assertEquals(100, tokens[0]);
        assertNotEquals(101, tokens[tokens.length - 1]);
    }

    @Test
    public void testGivenEncodeWithEosOnlyThenBosAbsent() {
        int[] tokens = tokenizer.encode("hi", false, true);
        assertNotEquals(100, tokens[0]);
        assertEquals(101, tokens[tokens.length - 1]);
    }

    // -----------------------------------------------------------------------
    // encode — with special tokens enabled
    // -----------------------------------------------------------------------

    @Test
    public void testGivenSpecialTokenInTextWhenEncodeWithSpecialEnabledThenEncodedAsSpecial() {
        tokenizer.allowSpecialTokens(true);
        int[] tokens = tokenizer.encode("<bos>");
        assertEquals(1, tokens.length);
        assertEquals(100, tokens[0]);
        tokenizer.allowSpecialTokens(false); // restore
    }

    @Test
    public void testGivenSpecialTokenInTextWhenEncodeWithSpecialDisabledThenEncodedAsText() {
        tokenizer.allowSpecialTokens(false);
        int[] tokens = tokenizer.encode("<bos>");
        // "<bos>" as text: 5 chars each encoded separately (no bigrams match)
        assertTrue(tokens.length > 1, "should be multiple tokens when treated as text");
        assertNotEquals(100, tokens[0], "should not produce the special token ID");
    }

    // -----------------------------------------------------------------------
    // decode — roundtrip
    // -----------------------------------------------------------------------

    @Test
    public void testGivenEncodedTextWhenDecodedThenOriginalTextRestored() {
        String original = "hello world";
        int[] tokens = tokenizer.encode(original, false, false);
        assertEquals(original, tokenizer.decode(tokens));
    }

    @Test
    public void testGivenSingleCharTokenWhenDecodedThenSingleCharRestored() {
        // ' ' = rank 0
        assertEquals(" ", tokenizer.decode(new int[]{0}));
    }

    /**
     * Regression test for the buffer overflow bug.
     * A special token {@code <sep>} has 5 bytes — well within the old 10-byte
     * budget for a 1-element array, but the exact-size allocation should also
     * work correctly.
     */
    @Test
    public void testGivenLongSpecialTokenWhenDecodedThenCorrectString() {
        tokenizer.allowSpecialTokens(true);
        // encode "<sep>" as a special token (rank 102) then decode it
        int[] tokens = tokenizer.encode("<sep>");
        assertEquals("<sep>", tokenizer.decode(tokens));
        tokenizer.allowSpecialTokens(false); // restore
    }

    @Test
    public void testGivenDecodeWithBosEosTokensThenSpecialStringsInOutput() {
        int[] tokens = {100, 72, 101}; // <bos>, 'h', <eos>
        String result = tokenizer.decode(tokens);
        assertEquals("<bos>h<eos>", result);
    }

    // -----------------------------------------------------------------------
    // tryDecode — valid UTF-8
    // -----------------------------------------------------------------------

    @Test
    public void testGivenValidUtf8TokensWhenTryDecodedThenSameAsDecodeForAscii() throws CharacterCodingException {
        String original = "hello world";
        int[] tokens = tokenizer.encode(original, false, false);
        assertEquals(original, tokenizer.tryDecode(tokens));
    }

    // -----------------------------------------------------------------------
    // encode/decode for various text cases
    // -----------------------------------------------------------------------

    @Test
    public void testGivenTextWithMultipleBigramsWhenEncodedThenAllMerged() {
        // "hello" contains "he"(95) + "l"(76) + "lo"(96)
        // 'l' = ASCII 108, rank = 108-32 = 76
        int[] tokens = tokenizer.encode("hello");
        assertArrayEquals(new int[]{95, 76, 96}, tokens);
    }

    @Test
    public void testGivenTextWithSpaceAndWordWhenEncodedThenSpaceAndWordTokenized() {
        // With our REGEX ([^\s]+|\s+), " world" is split into " "(rank 0) + "world"
        // "world": w=87, or=98, ld=99 (bigrams)
        // 'w' = ASCII 119, rank = 87
        int[] tokens = tokenizer.encode(" world");
        assertArrayEquals(new int[]{0, 87, 98, 99}, tokens);
    }

    @Test
    public void testGivenSingleSpaceWhenEncodedThenRankZero() {
        int[] tokens = tokenizer.encode(" ");
        assertArrayEquals(new int[]{0}, tokens);
    }

    @Test
    public void testGivenPunctuationWhenEncodedAndDecodedThenRoundtripCorrect() {
        String text = "Hello, World!";
        assertEquals(text, tokenizer.decode(tokenizer.encode(text)));
    }

    // -----------------------------------------------------------------------
    // Tokenizer interface — encode(String) shorthand
    // -----------------------------------------------------------------------

    @Test
    public void testGivenEncodeShorthandWhenCalledThenEquivalentToEncodeWithoutBosEos() {
        int[] a = tokenizer.encode("hello");
        int[] b = tokenizer.encode("hello", false, false);
        assertArrayEquals(a, b);
    }

    // -----------------------------------------------------------------------
    // Tokenizer.tiktoken static factory (via Tokenizer interface)
    // -----------------------------------------------------------------------

    @Test
    public void testGivenValidTiktokenFileWhenLoadedThenMapIsPopulated() throws IOException {
        // Uses the minimal test resource file created during test setup
        Map<Bytes, Integer> map = Tiktoken.load("deep/src/test/resources/tokenizer/minimal.tiktoken");
        assertFalse(map.isEmpty(), "loaded map must not be empty");
        // ' ' = rank 0 in our file
        assertEquals(0, map.get(new Bytes(new byte[]{(byte) ' '})));
    }

    @Test
    public void testGivenNonexistentFileWhenLoadCalledThenThrowsIOException() {
        assertThrows(IOException.class,
                () -> Tiktoken.load("nonexistent/path/that/does/not/exist.tiktoken"));
    }
}


