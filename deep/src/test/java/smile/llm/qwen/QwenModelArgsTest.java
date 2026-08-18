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
package smile.llm.qwen;

import java.io.IOException;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link QwenModelArgs}.
 *
 * @author Haifeng Li
 */
public class QwenModelArgsTest {

    @Test
    public void testGivenTinyConfigWhenLoadedThenFieldsMatch() throws IOException {
        QwenModelArgs args = QwenModelArgs.fromHuggingFace(
                "deep/src/test/resources/qwen/config_tiny.json", 2, 128);
        assertEquals(64, args.dim());
        assertEquals(4, args.numLayers());
        assertEquals(4, args.numHeads());
        assertEquals(2, args.numKvHeads());
        assertEquals(16, args.headDim());
        assertEquals(100, args.vocabSize());
        assertEquals(128, args.intermediateSize());
        assertEquals(0.25, args.partialRotaryFactor(), 1e-9);
        assertEquals(4, args.linearConvKernelDim());
        assertEquals(2, args.linearNumKeyHeads());
        assertEquals(4, args.linearNumValueHeads());
        assertEquals(3, args.numLinearAttentionLayers());
        assertEquals(1, args.numFullAttentionLayers());
        assertEquals(-1, args.fullAttentionLayerIndex(0));
        assertEquals(0, args.fullAttentionLayerIndex(3));
        assertEquals(0, args.linearAttentionLayerIndex(0));
        assertEquals(2, args.linearAttentionLayerIndex(2));
        assertEquals(2, args.maxBatchSize());
        assertEquals(128, args.maxSeqLen());
        assertEquals(4, args.rotaryDim()); // 16 * 0.25
        assertEquals(1, args.kvCacheLayout().numLayers());
        assertEquals(16, args.kvCacheLayout().headDim());
    }

    @Test
    public void testGiven27bWrapperConfigWhenLoadedThenUsesTextConfig() throws IOException {
        QwenModelArgs args = QwenModelArgs.fromHuggingFace(
                "deep/src/test/resources/qwen/config_27b_text.json", 1, 4096);
        assertEquals(5120, args.dim());
        assertEquals(64, args.numLayers());
        assertEquals(24, args.numHeads());
        assertEquals(4, args.numKvHeads());
        assertEquals(256, args.headDim());
        assertEquals(248320, args.vocabSize());
        assertEquals(17408, args.intermediateSize());
        assertEquals(1e7, args.ropeTheta(), 1.0);
        assertEquals(48, args.numLinearAttentionLayers());
        assertEquals(16, args.numFullAttentionLayers());
        assertEquals(QwenModelArgs.FULL_ATTENTION, args.layerTypes()[3]);
        assertEquals(QwenModelArgs.LINEAR_ATTENTION, args.layerTypes()[0]);
    }

    @Test
    public void testGivenMissingConfigWhenLoadedThenThrows() {
        assertThrows(IOException.class,
                () -> QwenModelArgs.fromHuggingFace("nonexistent/config.json", 1, 128));
    }

    @Test
    public void testGivenDefaultLayerTypesThenThreeToOnePattern() {
        String[] types = QwenModelArgs.defaultLayerTypes(8, 4);
        assertArrayEquals(new String[]{
                QwenModelArgs.LINEAR_ATTENTION,
                QwenModelArgs.LINEAR_ATTENTION,
                QwenModelArgs.LINEAR_ATTENTION,
                QwenModelArgs.FULL_ATTENTION,
                QwenModelArgs.LINEAR_ATTENTION,
                QwenModelArgs.LINEAR_ATTENTION,
                QwenModelArgs.LINEAR_ATTENTION,
                QwenModelArgs.FULL_ATTENTION
        }, types);
    }
}
