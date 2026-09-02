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
package smile.llm.model.qwen;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.activation.SiLU;
import smile.deep.layer.Layer;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_parameter;

/**
 * Gated RMSNorm used inside Gated DeltaNet:
 * {@code weight * rms_norm(x) * silu(gate)} with weight initialized to ones.
 *
 * @author Haifeng Li
 */
public class QwenRMSNormGated implements Layer {
    private final MemorySegment module;
    private final double eps;
    final Tensor weight;
    private final SiLU silu = new SiLU(false);

    /**
     * Constructor.
     * @param dim feature dimension.
     * @param eps numerical stability epsilon.
     */
    public QwenRMSNormGated(int dim, double eps) {
        this.eps = eps;
        this.weight = Tensor.ones(dim);
        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(arena.allocateFrom("QwenRMSNormGated")));
            smile_module_register_parameter(module, arena.allocateFrom("weight"), weight.handle());
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    /**
     * Forward with gating.
     * @param input hidden states.
     * @param gate  gate tensor (same shape as input).
     * @return gated normalized tensor.
     */
    public Tensor forward(Tensor input, Tensor gate) {
        Tensor fused = Native.rmsNormGated(input, gate, weight, eps);
        if (fused != null) {
            return fused;
        }
        try (Tensor x = input.to(ScalarType.Float);
             Tensor x2 = x.pow(2);
             Tensor mean = x2.mean(-1, true);
             Tensor denom = mean.add(eps).rsqrt_();
             Tensor xNorm = x.mul(denom);
             Tensor normalized = xNorm.mul(weight);
             Tensor gateF = gate.to(ScalarType.Float);
             Tensor gateAct = silu.forward(gateF);
             Tensor gated = normalized.mul(gateAct)) {
            return gated.to(input.dtype());
        }
    }

    @Override
    public Tensor forward(Tensor input) {
        throw new UnsupportedOperationException("QwenRMSNormGated requires a gate; use forward(x, gate)");
    }

    @Override
    public MemorySegment module() {
        return module;
    }
}
