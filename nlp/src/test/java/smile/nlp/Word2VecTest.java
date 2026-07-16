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
package smile.nlp;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Word2Vec}.
 * @author Haifeng Li
 */
public class Word2VecTest {
    private static Word2Vec model;
    @BeforeAll
    static void loadModel() throws IOException, URISyntaxException {
        Path path = Path.of(Word2VecTest.class.getResource("/smile/nlp/test_glove.txt").toURI());
        model = Word2Vec.glove(path);
    }
    @Test public void testVocabularySize() { assertEquals(10, model.size()); }
    @Test public void testDimension() { assertEquals(4, model.dimension()); }
    @Test public void testWordsFirstAndLast() {
        assertEquals("king", model.words[0]);
        assertEquals("france", model.words[9]);
    }
    @Test public void testContainsKnownWord() {
        assertTrue(model.contains("king"));
        assertTrue(model.contains("computer"));
    }
    @Test public void testContainsUnknownWord() { assertFalse(model.contains("banana")); }
    @Test public void testLookupKnownWord() {
        Optional<float[]> result = model.lookup("king");
        assertTrue(result.isPresent());
        float[] v = result.get();
        assertEquals(4, v.length);
        assertEquals(0.50f, v[0], 1e-4f);
        assertEquals(0.60f, v[1], 1e-4f);
        assertEquals(0.10f, v[2], 1e-4f);
        assertEquals(0.20f, v[3], 1e-4f);
    }
    @Test public void testLookupUnknownWordReturnsEmpty() {
        assertTrue(model.lookup("banana").isEmpty());
    }
    @Test public void testSimilaritySelfIsOne() {
        OptionalDouble sim = model.similarity("king", "king");
        assertTrue(sim.isPresent());
        assertEquals(1.0, sim.getAsDouble(), 1e-5);
    }
    @Test public void testSimilarityKingQueenHigherThanKingCat() {
        double simKQ = model.similarity("king", "queen").getAsDouble();
        double simKC = model.similarity("king", "cat").getAsDouble();
        assertTrue(simKQ > simKC);
    }
    @Test public void testSimilarityManWomanHigherThanManComputer() {
        double simMW = model.similarity("man", "woman").getAsDouble();
        double simMC = model.similarity("man", "computer").getAsDouble();
        assertTrue(simMW > simMC);
    }
    @Test public void testSimilarityCatDogHigherThanCatParis() {
        double simCD = model.similarity("cat", "dog").getAsDouble();
        double simCP = model.similarity("cat", "paris").getAsDouble();
        assertTrue(simCD > simCP);
    }
    @Test public void testSimilaritySymmetric() {
        double s1 = model.similarity("king", "queen").getAsDouble();
        double s2 = model.similarity("queen", "king").getAsDouble();
        assertEquals(s1, s2, 1e-9);
    }
    @Test public void testSimilarityUnknownReturnsEmpty() {
        assertTrue(model.similarity("king", "banana").isEmpty());
        assertTrue(model.similarity("foo", "bar").isEmpty());
    }
    @Test public void testGloVeEmptyFileThrows() throws IOException {
        Path tmp = Files.createTempFile("glove_empty", ".txt");
        tmp.toFile().deleteOnExit();
        assertThrows(IllegalArgumentException.class, () -> Word2Vec.glove(tmp));
    }
}
