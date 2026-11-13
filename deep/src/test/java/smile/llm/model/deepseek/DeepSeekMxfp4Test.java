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
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import smile.deep.CUDA;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Aten;
import smile.torch.Native;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Opt-in (CUDA, slice shards in {@code DEEPSEEK_DIR}): the NVFP4 conversion of real MXFP4 experts dequantizes to the
 * same values as {@link DeepSeekOps#dequantFp4}.
 *
 * @author Haifeng Li
 */
@Tag("cuda")
public class DeepSeekMxfp4Test {
    @Test
    public void testGivenRealExpertsThenNvfp4DequantizeEqualsMxfp4Dequantize() throws Exception {
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Assumptions.assumeTrue(Files.exists(dir.resolve("model-00003-of-00048.safetensors")), "no shards");
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
        DeepSeekCheckpoint ck = new DeepSeekCheckpoint(dir);
        for (String name : new String[]{"layers.0.ffn.experts.7.w1", "layers.3.ffn.experts.200.w2", "layers.20.ffn.experts.11.w3"}) {
            Tensor w = ck.get(name + ".weight");
            Tensor s = ck.get(name + ".scale");
            Tensor want = DeepSeekOps.dequantFp4(w, s).to(ScalarType.BFloat16);
            Tensor packed = Aten.one("aten::view.dtype", w, ScalarType.UInt8).to(cuda);
            Tensor got = Native.nvfp4Dequantize(packed, DeepSeekMxfp4.toNvfp4(s).to(cuda), DeepSeekMxfp4.GLOBAL);
            double diff = Aten.one("aten::max", Aten.one("aten::abs",
                    Aten.one("aten::sub.Tensor", got.to(Device.CPU()).to(ScalarType.Float), want.to(ScalarType.Float)))).doubleValue();
            assertEquals(0.0, diff, name);
        }
    }
}
