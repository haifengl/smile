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
package smile.deep.layer;

import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.pytorch.AvgPool2dImpl;
import org.bytedeco.pytorch.Module;
import smile.deep.tensor.Tensor;

/**
 * An average pooling layer that reduces a tensor by combining cells,
 * and assigning the average value of the input cells to the output cell.
 *
 * @author Haifeng Li
 */
public class AvgPool2dLayer implements Layer {
    private final AvgPool2dImpl module;

    /**
     * Constructor.
     * @param kernel the window/kernel size.
     */
    public AvgPool2dLayer(int kernel) {
        this(kernel, kernel);
    }

    /**
     * Constructor.
     * @param height the window/kernel height.
     * @param width the window/kernel width.
     */
    public AvgPool2dLayer(int height, int width) {
        try (var kernel = new LongPointer(height, width)) {
            this.module = new AvgPool2dImpl(kernel);
        }
    }

    @Override
    public Module asTorch() {
        return module;
    }

    @Override
    public Tensor forward(Tensor input) {
        return new Tensor(module.forward(input.asTorch()));
    }
}
