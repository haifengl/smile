/*
 * Copyright (c) 2010-2026 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm.model.deepseek;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests of {@link DeepSeekTokenizer} against the real {@code tokenizer.json}. Skipped unless a checkpoint
 * directory is given by {@code DEEPSEEK_DIR} (default {@code /raid/haili/dsv4/flash_raw}).
 *
 * <p>The expected ids of the plain-text cases were computed from the merge list of {@code tokenizer.json} with
 * an independent byte-level BPE on pieces whose pre-tokenization is unambiguous.
 */
public class DeepSeekTokenizerTest {
    private static DeepSeekTokenizer tokenizer;

    @BeforeAll
    static void load() throws Exception {
        String dir = System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw");
        Assumptions.assumeTrue(Files.exists(Path.of(dir, "tokenizer.json")), "no DeepSeek tokenizer.json in " + dir);
        tokenizer = DeepSeekTokenizer.of(dir);
    }

    @Test
    public void testGivenControlTokensThenIdsMatchCheckpoint() {
        assertEquals(0, tokenizer.specialToken(DeepSeekTokenizer.BOS));
        assertEquals(1, tokenizer.specialToken(DeepSeekTokenizer.EOS));
        assertEquals(2, tokenizer.pad());
        assertEquals(128803, tokenizer.specialToken("<｜User｜>"));
        assertEquals(128804, tokenizer.specialToken("<｜Assistant｜>"));
        assertEquals(128821, tokenizer.specialToken("<think>"));
        assertEquals(128822, tokenizer.specialToken("</think>"));
        assertEquals(128825, tokenizer.specialToken("｜DSML｜"));
        assertArrayEquals(new int[]{1}, tokenizer.stopTokens());
    }

    @Test
    public void testGivenPlainTextThenIdsMatchMergeListBpe() {
        assertArrayEquals(new int[]{19923, 2058}, tokenizer.encode("Hello world"));
        assertArrayEquals(new int[]{6895, 1883}, tokenizer.encode("12345"));
        assertArrayEquals(new int[]{14907, 1878}, tokenizer.encode("Tokenization"));
        assertArrayEquals(new int[]{30594}, tokenizer.encode("你好"));
        assertArrayEquals(new int[]{2720, 91223}, tokenizer.encode("naïve"));
        assertArrayEquals(new int[]{271}, tokenizer.encode("\n\n"));
        assertArrayEquals(new int[]{671, 4787}, tokenizer.encode("The quick"));
    }

    @Test
    public void testGivenIsolatedSplitsThenGapsAreKept() {
        // digits and CJK are cut out first, the text between them keeps being tokenized
        assertArrayEquals(new String[]{"abc", "123", "def"}, tokenizer.tokenize("abc123def"));
        assertArrayEquals(new String[]{"hi", "你好", "ok"}, tokenizer.tokenize("hi你好ok"));
        assertArrayEquals(new String[]{"12", " ", "3"}, tokenizer.tokenize("12 3"));
    }

    @Test
    public void testGivenChatMarkersThenSingleTokens() {
        int[] ids = tokenizer.encodeWithSpecials("<｜begin▁of▁sentence｜><｜User｜>hi<｜Assistant｜></think>");
        assertEquals(0, ids[0]);
        assertEquals(128803, ids[1]);
        assertEquals(128804, ids[ids.length - 2]);
        assertEquals(128822, ids[ids.length - 1]);
        // without special handling the marker is ordinary text
        assertTrue(tokenizer.encode("<｜User｜>").length > 1);
    }

    @Test
    public void testGivenVariedTextThenDecodeRoundTrips() throws Exception {
        for (String s : new String[]{"Hello, world!", "a  b\t\tc\n\n\nd ", " leading and trailing  ", "price: $12,345.67 (approx.)",
                "你好，世界！こんにちは カタカナ", "emoji 😀🎉 and accents café Ünïcode", "def f(x):\n    return x**2  # square\n",
                "ＡＢＣ fullwidth ｜ bars", "", "   ", "1234567890", "<html><body>x</body></html>"}) {
            assertEquals(s, tokenizer.decode(tokenizer.encode(s)), "round trip of [" + s + "]");
        }
    }

    @Test
    public void testGivenDisplayDecodeThenMarkersHiddenAndThinkAndDsmlKept() throws Exception {
        int[] ids = tokenizer.encodeWithSpecials("<｜User｜>q<｜Assistant｜><think>r</think>a<｜DSML｜ calls><｜end▁of▁sentence｜>");
        assertEquals("q<think>r</think>a<｜DSML｜ calls>", tokenizer.tryDecode(ids, true));
        assertEquals("<｜User｜>q<｜Assistant｜><think>r</think>a<｜DSML｜ calls><｜end▁of▁sentence｜>", tokenizer.tryDecode(ids, false));
    }
}
