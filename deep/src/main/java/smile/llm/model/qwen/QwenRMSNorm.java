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
import smile.deep.layer.Layer;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import smile.torch.Native;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_parameter;

/**
 * Qwen3.5 RMSNorm: {@code rms_norm(x) * (1 + weight)} with weight initialized to zeros.
 *
 * @author Haifeng Li
 */
public class QwenRMSNorm implements Layer {
    private final MemorySegment module;
    private final double eps;
    final Tensor weight;

    /**
     * Constructor.
     * @param dim feature dimension.
     * @param eps numerical stability epsilon.
     */
    public QwenRMSNorm(int dim, double eps) {
        this.eps = eps;
        this.weight = Tensor.zeros(dim);
        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(arena.allocateFrom("QwenRMSNorm")));
            smile_module_register_parameter(module, arena.allocateFrom("weight"), weight.handle());
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));
    }

    @Override
    public Tensor forward(Tensor input) {
        Tensor fused = Native.rmsNorm(input, weight, eps);
        if (fused != null) {
            return fused;
        }
        try (Tensor x = input.to(ScalarType.Float);
             Tensor x2 = x.pow(2);
             Tensor mean = x2.mean(-1, true);
             Tensor denom = mean.add(eps).rsqrt_();
             Tensor normalized = x.mul(denom);
             Tensor scale = weight.add(1.0);
             Tensor scaled = normalized.mul(scale)) {
            // Safe without clone: Tensor.close() detaches from push scopes, and
            // to() returns a distinct ST_Tensor wrapper (refcount keeps storage).
            return scaled.to(input.dtype());
        }
    }

    @Override
    public MemorySegment module() {
        return module;
    }
}
