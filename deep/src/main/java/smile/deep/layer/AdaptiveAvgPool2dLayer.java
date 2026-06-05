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
import static smile.deep.tensor.Native.check;
import static smile.torch.smile_torch_h.*;

/**
 * An adaptive average pooling that reduces a tensor by combining cells.
 * The output size should be specified and the stride and kernel-size are
 * automatically selected to adapt to the needs.
 *
 * @author Haifeng Li
 */
public class AdaptiveAvgPool2dLayer extends TypedLayer {
    /**
     * Constructor.
     * @param size the output size.
     */
    public AdaptiveAvgPool2dLayer(int size) {
        this(size, size);
    }

    /**
     * Constructor.
     * @param height the output height.
     * @param width the output width.
     */
    public AdaptiveAvgPool2dLayer(int height, int width) {
        super(create(height, width));
    }

    private static Handles create(int height, int width) {
        MemorySegment h;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outputSize = arena.allocateFrom(JAVA_LONG, height, width);
            h = check(smile_adaptive_avgpool2d_create(outputSize));
        }
        MemorySegment m = check(smile_adaptive_avgpool2d_as_module(h));
        return new Handles(h, m, () -> {
            smile_module_free(m);
            smile_adaptive_avgpool2d_free(h);
        });
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(smile_adaptive_avgpool2d_forward(handle, input.handle()));
    }
}
