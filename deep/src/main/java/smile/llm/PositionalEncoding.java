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

import org.bytedeco.pytorch.*;
import org.bytedeco.pytorch.Module;
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
public class PositionalEncoding {
    Module module;
    DropoutImpl dropout;
    Tensor pe;

    public PositionalEncoding(int dModel) {
        this(dModel, 0.1, 5000);
    }

    public PositionalEncoding(int dModel, double dropout, int maxLen) {
        this.module = new Module();
        this.dropout = new DropoutImpl(dropout);
        this.pe = torch.zeros(maxLen, dModel);
        Tensor position = torch.arange(new Scalar(0), new Scalar(maxLen), new Scalar(1)).unsqueeze(1);
        Tensor divTerm = torch.arange(new Scalar(0), new Scalar(dModel), new Scalar(2)).exp_().mul_(new Scalar(-Math.log(10000.0) / dModel));
        position.mul_(divTerm);
        TensorIndexVector i0 = new TensorIndexVector(
                new TensorIndex(new Slice()),
                new TensorIndex(new Slice(new SymIntOptional(new SymInt(0)), new SymIntOptional(), new SymIntOptional(new SymInt(2))))
        );
        TensorIndexVector i1 = new TensorIndexVector(
                new TensorIndex(new Slice()),
                new TensorIndex(new Slice(new SymIntOptional(new SymInt(1)), new SymIntOptional(), new SymIntOptional(new SymInt(2))))
        );
        pe.index_put_(i0, position.sin());
        pe.index_put_(i1, position.cos());
        pe = pe.unsqueeze(0).transpose(0, 1);
        module.register_buffer("pe", pe);
    }

    /**
     * Returns the positional encoding of a sequence.
     * @param x the sequence fed to the positional encoder model.
     * @return the encoded tensor.
     */
    public smile.deep.Tensor forward(smile.deep.Tensor x) {
        org.bytedeco.pytorch.Tensor p = pe.index(new TensorIndexVector(
                new TensorIndex(new Slice(new SymIntOptional(), new SymIntOptional(new SymInt(x.size(0))), new SymIntOptional())),
                new TensorIndex(new Slice())
        ));
        org.bytedeco.pytorch.Tensor xp = x.value().add(p);
        return smile.deep.Tensor.of(dropout.forward(xp));
    }
}
