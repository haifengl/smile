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
package smile.llm.transformer;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.layer.Layer;
import smile.deep.tensor.Device;
import smile.torch.Native;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import static smile.deep.tensor.Index.*;
import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.smile_module_create;
import static smile.torch.smile_torch_h.smile_module_free;
import static smile.torch.smile_torch_h.smile_module_register_buffer;

/**
 * Positional encoding in original Transformer. Positional encoding injects
 * some information about the absolute position of the tokens in the sequence.
 * The positional encodings have the same dimension as the embeddings, so that
 * the two can be summed. This class uses sine and cosine functions of
 * different frequencies.
 *
 * @author Haifeng Li
 */
public class PositionalEncoding implements Layer {
    private final MemorySegment module;
    /** The positional encoding tensor. */
    private Tensor pe;

    /**
     * Constructor.
     * @param dim the dimension of the frequency tensor.
     * @param end the end index for precomputing frequencies.
     */
    public PositionalEncoding(int dim, int end) {
        this(dim, end, 10000.0);
    }

    /**
     * Constructor.
     * @param dim the dimension of the frequency tensor.
     * @param end the end index for precomputing frequencies.
     * @param theta the scaling factor for frequency computation.
     */
    public PositionalEncoding(int dim, int end, double theta) {
        try (Arena arena = Arena.ofConfined()) {
            this.module = check(smile_module_create(arena.allocateFrom("PositionalEncoding")));
        }
        MemorySegment m = this.module;
        Native.CLEANER.register(this, () -> smile_module_free(m));

        try (Tensor position = Tensor.arange(0, end, 1).to(ScalarType.Float).unsqueeze(1);
             Tensor divTerm = Tensor.arange(0, dim, 2).to(ScalarType.Float).mul_(-Math.log(theta) / dim).exp_();
             Tensor angles  = position.mul(divTerm)) {
            pe = Tensor.zeros(end, dim);
            try (Tensor sinVal = angles.sin();
                 Tensor cosVal = angles.cos();
                 var even = slice(0, null, 2);
                 var odd = slice(1, null, 2)) {
                pe.put_(sinVal, Colon, even);
                pe.put_(cosVal, Colon, odd);
            }
            pe.setRequireGrad(false);
            registerBuffer(pe);
        }
    }

    /** Registers the positional-encoding tensor as the module's "pe" buffer. */
    private void registerBuffer(Tensor tensor) {
        try (Arena arena = Arena.ofConfined()) {
            smile_module_register_buffer(module, arena.allocateFrom("pe"), tensor.handle());
        }
    }

    @Override
    public Tensor forward(Tensor input) {
        try (var rows = slice(null, input.size(0));
             Tensor p = pe.get(rows, Colon)) {
            return input.add(p);
        }
    }

    @Override
    public MemorySegment module() {
        return module;
    }

    /**
     * Moves the encoder to a device.
     * @param device the compute device.
     * @return this encoder.
     */
    public PositionalEncoding to(Device device) {
        Tensor newPe = pe.to(device);
        registerBuffer(newPe);
        pe.close();
        pe = newPe;
        return this;
    }
}
