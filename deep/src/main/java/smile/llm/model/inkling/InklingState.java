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
package smile.llm.model.inkling;

import smile.deep.tensor.Device;
import smile.deep.tensor.ScalarType;
import smile.deep.tensor.Tensor;

import static smile.llm.model.inkling.Ix.*;

/**
 * The state of one sequence in the reference model: for every layer application a key and a value cache
 * {@code [kvHeads, capacity, headDim]} (bf16) and the last three inputs of its four short convolutions (float32).
 * Applications of a looped layer in different passes have separate state.
 *
 * @author Haifeng Li
 */
final class InklingState implements AutoCloseable {
    /** Convolution slots of an application. */
    static final int CONV_K = 0, CONV_V = 1, CONV_ATTN = 2, CONV_MLP = 3;

    final int capacity;
    final Tensor[] k;
    final Tensor[] v;
    final Tensor[][] conv;
    /** Tokens processed so far. */
    int length;

    InklingState(InklingModelArgs args, int capacity, Device device) {
        this.capacity = capacity;
        int apps = args.numApplications();
        k = new Tensor[apps];
        v = new Tensor[apps];
        conv = new Tensor[apps][4];
        for (int a = 0; a < apps; a++) {
            int layer = args.layerOfApplication(a);
            int heads = args.isLocal(layer) ? args.swaKvHeads() : args.numKvHeads();
            int dim = args.isLocal(layer) ? args.swaHeadDim() : args.headDim();
            k[a] = keep(zeros(device, ScalarType.BFloat16, heads, capacity, dim));
            v[a] = keep(zeros(device, ScalarType.BFloat16, heads, capacity, dim));
            int rows = args.sconvKernel() - 1;
            conv[a][CONV_K] = keep(zeros(device, ScalarType.Float, rows, heads * dim));
            conv[a][CONV_V] = keep(zeros(device, ScalarType.Float, rows, heads * dim));
            conv[a][CONV_ATTN] = keep(zeros(device, ScalarType.Float, rows, args.dim()));
            conv[a][CONV_MLP] = keep(zeros(device, ScalarType.Float, rows, args.dim()));
        }
    }

    @Override
    public void close() {
        for (int a = 0; a < k.length; a++) {
            k[a].close();
            v[a].close();
            for (Tensor c : conv[a]) {
                c.close();
            }
        }
    }
}
