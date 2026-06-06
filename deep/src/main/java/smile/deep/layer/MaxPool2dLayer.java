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

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import smile.deep.tensor.Tensor;

import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static smile.torch.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * A max pooling layer that reduces a tensor by combining cells,
 * and assigning the maximum value of the input cells to the output cell.
 *
 * @author Haifeng Li
 */
public class MaxPool2dLayer extends ModuleLayer {
    /**
     * Constructor.
     * @param kernel the window/kernel size.
     */
    public MaxPool2dLayer(int kernel) {
        this(kernel, kernel);
    }

    /**
     * Constructor.
     * @param height the window/kernel height.
     * @param width the window/kernel width.
     */
    public MaxPool2dLayer(int height, int width) {
        super(create(height, width));
    }

    private static Handles create(int height, int width) {
        MemorySegment h;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment kernel = arena.allocateFrom(JAVA_LONG, height, width);
            h = check(smile_maxpool2d_create(kernel, MemorySegment.NULL, MemorySegment.NULL));
        }
        MemorySegment m = check(smile_maxpool2d_as_module(h));
        return new Handles(h, m, () -> {
            smile_module_free(m);
            smile_maxpool2d_free(h);
        });
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(smile_maxpool2d_forward(handle, input.handle()));
    }
}
