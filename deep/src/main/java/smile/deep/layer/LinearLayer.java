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
package smile.deep.layer;

import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Tensor;

import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * A fully connected linear layer.
 *
 * @author Haifeng Li
 */
public class LinearLayer extends TypedLayer {
    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     */
    public LinearLayer(int in, int out) {
        this(in, out, true);
    }

    /**
     * Constructor.
     * @param in the number of input features.
     * @param out the number of output features.
     * @param bias If false, the layer will not learn an additive bias.
     */
    public LinearLayer(int in, int out, boolean bias) {
        super(create(in, out, bias));
    }

    private static Handles create(int in, int out, boolean bias) {
        MemorySegment h = check(smile_linear_create(in, out, bias ? 1 : 0));
        MemorySegment m = check(smile_linear_as_module(h));
        return new Handles(h, m, () -> {
            smile_module_free(m);
            smile_linear_free(h);
        });
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(smile_linear_forward(handle, input.handle()));
    }
}
