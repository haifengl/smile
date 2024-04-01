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
package smile.llm;

import org.bytedeco.pytorch.Module;
import smile.deep.layer.Layer;
import smile.deep.tensor.Device;
import smile.deep.tensor.Tensor;
import static smile.deep.tensor.Index.*;
import org.bytedeco.pytorch.global.torch;

/**
 * Positional encoding injects some information about the relative
 * or absolute position of the tokens in the sequence. The positional
 * encodings have the same dimension as the embeddings, so that the two
 * can be summed. This class uses sine and cosine functions of different
 * frequencies.
 *
 * @author Haifeng Li
 */
public class PositionalEncoding implements Layer {
    /** The module to register the buffer. */
    private Module module;
    /** The dropout probability. */
    private double dropout;
    /** The positional encoding tensor. */
    private Tensor pe;

    /**
     * Constructor.
     * @param dModel the number of expected features in the token embedding.
     */
    public PositionalEncoding(int dModel) {
        this(dModel, 0.1, 5000);
    }

    /**
     * Constructor.
     * @param dModel the number of expected features in the token embedding.
     * @param dropout the dropout probability.
     * @param maxLen the maximum length of token sequence.
     */
    public PositionalEncoding(int dModel, double dropout, int maxLen) {
        this.module = new Module();
        this.dropout = dropout;
        this.pe = Tensor.zeros(maxLen, dModel);
        Tensor position = Tensor.arange(0, maxLen,1).unsqueeze(1);
        Tensor divTerm = Tensor.arange(0, dModel, 2).exp_().mul_(-Math.log(10000.0) / dModel);
        position.mul_(divTerm);
        pe.put_(position.sin(), Colon, slice(0L, null, 2L));
        pe.put_(position.cos(), Colon, slice(1L, null, 2L));
        pe = pe.unsqueeze(0).transpose(0, 1);
        module.register_buffer("pe", pe.asTorch());
    }

    @Override
    public Tensor forward(Tensor x) {
        Tensor p = pe.get(
                slice(null, x.size(0)),
                Colon
        );
        Tensor xp = x.add(p);
        return Tensor.of(torch.dropout(xp.asTorch(), dropout, true));
    }

    @Override
    public void register(String name, Layer parent) {
        module = parent.asTorch().register_module(name, module);
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
        module.to(device.asTorch(), true);
        return this;
    }
}
