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
 * A convolutional layer.
 *
 * @author Haifeng Li
 */
public class Conv2dLayer extends ModuleLayer {
    /**
     * Constructor.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param kernel the window/kernel size.
     * @param stride controls the stride for the cross-correlation.
     * @param padding controls the amount of padding applied on both sides.
     * @param dilation controls the spacing between the kernel points.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     * @param paddingMode "zeros", "reflect", "replicate" or "circular".
     */
    public Conv2dLayer(int in, int out, int kernel, int stride, int padding, int dilation, int groups, boolean bias, String paddingMode) {
        super(create(in, out, kernel, stride, padding, dilation, groups, bias, paddingMode));
    }

    /**
     * Constructor.
     * @param in the number of input channels.
     * @param out the number of output channels/features.
     * @param kernel the window/kernel size.
     * @param stride controls the stride for the cross-correlation.
     * @param padding "valid" or "same". With "valid" padding, there's no
     *               "made-up" padding inputs. It drops the right-most columns
     *               (or bottom-most rows). "same" tries to pad evenly left
     *               and right, but if the amount of columns to be added
     *               is odd, it will add the extra column to the right.
     *               If stride is 1, the layer's outputs will have the
     *               same spatial dimensions as its inputs.
     * @param dilation controls the spacing between the kernel points.
     * @param groups controls the connections between inputs and outputs.
     *              The in channels and out channels must both be divisible by groups.
     * @param bias If true, adds a learnable bias to the output.
     * @param paddingMode "zeros", "reflect", "replicate" or "circular".
     */
    public Conv2dLayer(int in, int out, int kernel, int stride, String padding, int dilation, int groups, boolean bias, String paddingMode) {
        super(create(in, out, kernel, stride, symbolicPadding(padding, kernel, dilation), dilation, groups, bias, paddingMode));
    }

    /**
     * Converts a "valid"/"same" symbolic padding into a numeric padding.
     * The native API does not support symbolic padding, so "same" is realized
     * as symmetric padding {@code dilation*(kernel-1)/2}; this matches PyTorch
     * for odd kernel sizes with stride 1.
     */
    private static int symbolicPadding(String padding, int kernel, int dilation) {
        return switch (padding) {
            case "valid" -> 0;
            case "same" -> dilation * (kernel - 1) / 2;
            default -> throw new IllegalArgumentException("padding has to be either 'valid' or 'same', but got " + padding);
        };
    }

    private static int paddingMode(String paddingMode) {
        return switch (paddingMode) {
            case "zeros" -> ST_PAD_ZEROS();
            case "reflect" -> ST_PAD_REFLECT();
            case "replicate" -> ST_PAD_REPLICATE();
            case "circular" -> ST_PAD_CIRCULAR();
            default -> throw new IllegalArgumentException("paddingMode has to be either 'zeros', 'reflect', 'replicate' or 'circular', but got " + paddingMode);
        };
    }

    private static Handles create(int in, int out, int kernel, int stride, int padding, int dilation, int groups, boolean bias, String paddingMode) {
        int padMode = paddingMode(paddingMode);
        MemorySegment h;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment k = arena.allocateFrom(JAVA_LONG, kernel, kernel);
            MemorySegment s = arena.allocateFrom(JAVA_LONG, stride, stride);
            MemorySegment p = arena.allocateFrom(JAVA_LONG, padding, padding);
            MemorySegment d = arena.allocateFrom(JAVA_LONG, dilation, dilation);
            h = check(smile_conv2d_create(in, out, k, s, p, d, groups, bias ? 1 : 0, padMode));
        }
        MemorySegment m = check(smile_conv2d_as_module(h));
        return new Handles(h, m, () -> {
            smile_module_free(m);
            smile_conv2d_free(h);
        });
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(smile_conv2d_forward(handle, input.handle()));
    }
}
