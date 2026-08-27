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
package smile.llm.quant;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.llm.model.qwen.GatedDeltaNet;
import smile.llm.model.qwen.QwenModelArgs;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for Qwen native FP8 load helpers.
 *
 * @author Haifeng Li
 */
public class QuantizedQwenFp8LoaderTest {

    @BeforeEach
    public void requireNativeLib() {
        try {
            Tensor.of(new float[]{1f}).close();
        } catch (Throwable t) {
            assumeTrue(false, "libsmile_torch unavailable: " + t.getMessage());
        }
    }

    @Test
    public void testGivenFp8QuantConfigWhenDetectThenFp8() {
        ObjectNode root = new ObjectMapper().createObjectNode();
        ObjectNode qc = root.putObject("quantization_config");
        qc.put("quant_method", "fp8");
        qc.put("activation_scheme", "dynamic");
        qc.putArray("weight_block_size").add(128).add(128);
        assertEquals(QuantFormat.FP8, QuantFormatDetector.fromConfig(root));
    }

    @Test
    public void testGivenInstalledProjectionKeysWhenFilterThenMatchOfficialLayout() {
        assertTrue(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.0.mlp.gate_proj.weight"));
        assertTrue(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.0.mlp.gate_proj.weight_scale_inv"));
        assertTrue(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.3.self_attn.q_proj.weight"));
        assertTrue(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.0.linear_attn.in_proj_qkv.weight"));
        assertTrue(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.0.linear_attn.out_proj.weight_scale_inv"));

        // Residual dense in official Qwen3.8-FP8
        assertFalse(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.0.linear_attn.in_proj_a.weight"));
        assertFalse(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.0.linear_attn.in_proj_b.weight"));
        assertFalse(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.0.linear_attn.A_log"));
        assertFalse(QuantizedQwenFp8Loader.isInstalledProjectionKey(
                "model.language_model.layers.0.linear_attn.conv1d.weight"));
        assertFalse(QuantizedQwenFp8Loader.isInstalledProjectionKey("lm_head.weight"));
    }

    @Test
    public void testGivenHfPrefixesWhenResolveBaseThenPrefersLanguageModel() throws Exception {
        Map<String, String> map = new HashMap<>();
        map.put("model.language_model.layers.0.mlp.gate_proj.weight", "a.safetensors");
        map.put("model.layers.0.mlp.gate_proj.weight", "b.safetensors");
        assertEquals("model.language_model.layers.0.mlp.gate_proj",
                QuantizedQwenFp8Loader.resolveHfBase(map, 0, "mlp.gate_proj"));
    }

    @Test
    public void testGivenBlockScaleWhenDequantThenShapeAndDtypeMatch() {
        int n = 256;
        int k = 128;
        // Flat FP8-as-float then cast — use Half staging then Float8 if available
        float[] data = new float[n * k];
        for (int i = 0; i < data.length; i++) {
            data[i] = ((i % 17) - 8) * 0.05f;
        }
        Tensor wF = Tensor.of(data).reshape(n, k);
        Tensor wFp8;
        try {
            wFp8 = wF.to(ScalarType.Float8e4m3fn);
        } catch (Throwable t) {
            wF.close();
            assumeTrue(false, "Float8e4m3fn unavailable: " + t.getMessage());
            return;
        }
        wF.close();

        int nB = n / Fp8BlockDequant.BLOCK;
        int kB = k / Fp8BlockDequant.BLOCK;
        float[] scales = new float[nB * kB];
        for (int i = 0; i < scales.length; i++) {
            scales[i] = 0.1f;
        }
        Tensor scale = Tensor.of(scales).reshape(nB, kB).to(ScalarType.BFloat16);

        assertTrue(Fp8BlockDequant.isBlockScale(wFp8, scale));
        assertFalse(Fp8BlockDequant.isTensorScale(scale));

        Tensor dense = Fp8BlockDequant.dequant(wFp8, scale, ScalarType.BFloat16);
        assertArrayEquals(new long[]{n, k}, dense.shape());
        assertEquals(ScalarType.BFloat16, dense.dtype());
        dense.close();
        wFp8.close();
        scale.close();
    }

    @Test
    public void testGivenTensorScaleWhenClassifyThenTensorNotBlock() {
        Tensor scale = Tensor.of(new float[]{1.5f});
        Tensor w = Tensor.of(new float[]{1f, 2f, 3f, 4f}).reshape(2, 2);
        assertTrue(Fp8BlockDequant.isTensorScale(scale));
        assertFalse(Fp8BlockDequant.isBlockScale(w, scale));
        scale.close();
        w.close();
    }

    @Test
    public void testGivenDeltaNetWhenReplaceGemmThenForwardSmoke() {
        String[] types = {QwenModelArgs.LINEAR_ATTENTION};
        QwenModelArgs args = new QwenModelArgs(
                64,   // dim
                1,    // numLayers
                4,    // numHeads
                2,    // numKvHeads
                16,   // headDim
                100,  // vocabSize
                128,  // intermediateSize
                1e-6, // normEps
                10000.0, // ropeTheta
                0.25, // partialRotaryFactor
                4,    // linearConvKernelDim
                8,    // linearKeyHeadDim
                8,    // linearValueHeadDim
                2,    // linearNumKeyHeads
                4,    // linearNumValueHeads
                types,
                1,    // maxBatchSize
                32    // maxSeqLen
        );
        GatedDeltaNet delta = new GatedDeltaNet(args, 0, null);
        int hidden = args.dim();
        int keyDim = args.linearKeyHeadDim() * args.linearNumKeyHeads();
        int valueDim = args.linearValueHeadDim() * args.linearNumValueHeads();

        LinearOp qkv = new smile.deep.layer.LinearLayer(hidden, keyDim * 2 + valueDim, false);
        LinearOp z = new smile.deep.layer.LinearLayer(hidden, valueDim, false);
        LinearOp out = new smile.deep.layer.LinearLayer(valueDim, hidden, false);
        delta.replaceGemmProjections(qkv, z, out);

        Tensor x = Tensor.zeros(1, 2, hidden);
        Tensor y = delta.forward(x);
        assertArrayEquals(new long[]{1, 2, hidden}, y.shape());
        y.close();
        x.close();
    }

    @Test
    public void testGivenFp8LmHeadScaleWhenValidateThenFails() {
        Map<String, String> map = new HashMap<>();
        map.put("lm_head.weight", "a.safetensors");
        map.put("lm_head.weight_scale_inv", "a.safetensors");
        assertThrows(Exception.class,
                () -> QuantizedQwenFp8Loader.validateCheckpointPolicy(
                        java.nio.file.Path.of("."), map, false));
    }

    @Test
    public void testGivenVisionFp8WhenVlEnabledThenFails() {
        Map<String, String> map = new HashMap<>();
        map.put("model.visual.blocks.0.attn.proj.weight", "a.safetensors");
        map.put("model.visual.blocks.0.attn.proj.weight_scale_inv", "a.safetensors");
        assertThrows(Exception.class,
                () -> QuantizedQwenFp8Loader.validateCheckpointPolicy(
                        java.nio.file.Path.of("."), map, true));
    }
}
