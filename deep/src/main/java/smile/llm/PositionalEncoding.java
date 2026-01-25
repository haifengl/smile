/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
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
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.llm;

import org.bytedeco.pytorch.Module;
import smile.deep.layer.Layer;
import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;
import static smile.deep.tensor.Index.*;

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
    private final Module module = new Module("PositionalEncoding");
    /** The positional encoding tensor. */
    private final Tensor pe;

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
        try (Tensor position = Tensor.arange(0, end,1).to(ScalarType.Float32).unsqueeze(1);
             Tensor divTerm = Tensor.arange(0, dim, 2).to(ScalarType.Float32).mul_(-Math.log(theta) / dim).exp_()) {
            position.mul_(divTerm);
            pe = Tensor.zeros(end, dim);
            pe.put_(position.sin(), Colon, slice(0, null, 2));
            pe.put_(position.cos(), Colon, slice(1, null, 2));
            pe.setRequireGrad(false);
            module.register_buffer("pe", pe.asTorch());
        }
    }

    @Override
    public Tensor forward(Tensor input) {
        try (Tensor p = pe.get(slice(null, input.size(0)), Colon)) {
            return input.add(p);
        }
    }

    @Override
    public Module asTorch() {
        return module;
    }

    /**
     * Moves the encoder to a device.
     * @param device the compute device.
     * @return this encoder.
     */
    public PositionalEncoding to(Device device) {
        pe.to(device);
        return this;
    }
}
