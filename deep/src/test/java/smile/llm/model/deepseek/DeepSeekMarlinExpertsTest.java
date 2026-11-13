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

import java.io.File;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in (CUDA, slice shards): the Marlin MoE path of layer 3 gives the same routed-expert output as the dequantized
 * reference path, for a decode-sized and a prefill-sized batch.
 *
 * @author Haifeng Li
 */
@Tag("cuda")
public class DeepSeekMarlinExpertsTest {
    @Test
    public void testGivenRealLayerThenMarlinRoutedOutputMatchesDequantizedPath() throws Exception {
        Path dir = Path.of(System.getenv().getOrDefault("DEEPSEEK_DIR", "/raid/haili/dsv4/flash_raw"));
        Assumptions.assumeTrue(Files.exists(dir.resolve("model-00004-of-00048.safetensors")), "no shards");
        Assumptions.assumeTrue(CUDA.isAvailable() && CUDA.deviceCount() > 0 && Aten.isAvailable(), "no CUDA");
        Device cuda = Device.CUDA();
        DeepSeekModelArgs args = DeepSeekModelArgs.load(new File(dir.toFile(), "config.json")).slice(4);
        DeepSeekCheckpoint ck = new DeepSeekCheckpoint(dir);
        DeepSeekMoE moe = new DeepSeekMoE(args, ck, 3);
        try (DeepSeekMarlinExperts marlin = new DeepSeekMarlinExperts(ck, "layers.3.ffn", args.moeConfig(3)[0],
                args.moeConfig(3)[1], args.swigluLimit(), cuda)) {
            for (int tokens : new int[]{1, 40}) {
                Tensor x = Aten.one("aten::mul.Scalar", Aten.one("aten::randn", new long[]{tokens, args.dim()}), 1.0)
                        .to(ScalarType.BFloat16);
                Tensor[] r = moe.route(x);
                Tensor want = moe.routed(x, r[0], r[1]);
                Tensor got = marlin.forward(x.to(cuda), r[0].to(cuda), r[1].to(cuda)).to(Device.CPU());
                double num = Aten.one("aten::norm.Scalar", Aten.one("aten::sub.Tensor", got, want)).doubleValue();
                double den = Aten.one("aten::norm.Scalar", want).doubleValue();
                System.out.printf("tokens %d: routed rel err %.5f%n", tokens, num / den);
                assertTrue(num / den < 1e-3, "tokens " + tokens + " rel err " + num / den);
            }
        }
    }
}
