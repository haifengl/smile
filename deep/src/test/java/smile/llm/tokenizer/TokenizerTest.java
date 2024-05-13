/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
package smile.llm.tokenizer;

import java.io.IOException;
import java.util.Arrays;
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
    public void testSentencePiece() throws IOException {
        var tokenizer = Tokenizer.sentencePiece("deep/src/universal/models/llama_tokenizer_v2.model");
        System.out.println(Arrays.toString(tokenizer.tokenize("This is a test sentence.")));

        int[] tokens1 = { 1, 910, 338, 263, 1243, 10541, 29889, 2 };
        assertEquals("This is a test sentence.", tokenizer.decode(tokens1));
        assertArrayEquals(tokens1, tokenizer.encode("This is a test sentence.", true, true));

        int[] tokens2 = { 910, 338, 263, 1243, 10541, 29889, 2 };
        assertEquals("This is a test sentence.", tokenizer.decode(tokens2));
        assertArrayEquals(tokens2, tokenizer.encode("This is a test sentence.", false, true));

        int[] tokens3 = { 1, 910, 338, 263, 1243, 10541, 29889 };
        assertEquals("This is a test sentence.", tokenizer.decode(tokens3));
        assertArrayEquals(tokens3, tokenizer.encode("This is a test sentence.", true, false));

        int[] tokens4 = { 910, 338, 263, 1243, 10541, 29889 };
        assertEquals("This is a test sentence.", tokenizer.decode(tokens4));
        assertArrayEquals(tokens4, tokenizer.encode("This is a test sentence.", false, false));
    }
}
