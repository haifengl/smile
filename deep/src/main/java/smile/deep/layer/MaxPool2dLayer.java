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
package smile.deep.layer;

import org.bytedeco.javacpp.LongPointer;
import org.bytedeco.pytorch.MaxPool2dImpl;
import smile.deep.tensor.Tensor;

/**
 * A max pooling layer that reduces a tensor by combining cells,
 * and assigning the maximum value of the input cells to the output cell.
 *
 * @author Haifeng Li
 */
public class MaxPool2dLayer implements Layer {
    /** The window/kernel size. */
    int size;
    /** Implementation. */
    MaxPool2dImpl module;

    /**
     * Constructor.
     * @param size the window/kernel size.
     */
    public MaxPool2dLayer(int size) {
        this.size = size;
        LongPointer p = new LongPointer(1).put(size);
        this.module = new MaxPool2dImpl(p);
    }

    @Override
    public void register(String name, Layer parent) {
        this.module = parent.asTorch().register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor input) {
        return Tensor.of(module.forward(input.asTorch()));
    }

    @Override
    public MaxPool2dImpl asTorch() {
        return module;
    }
}
