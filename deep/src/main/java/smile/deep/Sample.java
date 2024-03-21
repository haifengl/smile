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
package smile.deep;

import smile.deep.tensor.*;

/**
 * A min-batch dataset consists of data and an associated target (label).
 *
 * @author Haifeng Li
 */
public class Sample {
    /** The data samples. */
    public final Tensor data;
    /** The sample labels. */
    public final Tensor target;

    /**
     * Constructor.
     * @param data the data samples.
     * @param target the sample labels.
     */
    public Sample(Tensor data, Tensor target) {
        this.data = data;
        this.target = target;
    }

    /**
     * Constructor.
     * @param data the data samples.
     * @param target the sample labels.
     */
    Sample(org.bytedeco.pytorch.Tensor data, org.bytedeco.pytorch.Tensor target) {
        this.data = Tensor.of(data);
        this.target = Tensor.of(target);
    }

    /**
     * Constructor.
     * @param data the data samples.
     * @param target the sample labels.
     * @param device the compute device of the samples.
     * @param dtype the element data type of the samples.
     */
    Sample(org.bytedeco.pytorch.Tensor data, org.bytedeco.pytorch.Tensor target, Device device, ScalarType dtype) {
        this.data = Tensor.of(data, device, dtype);
        this.target = Tensor.of(target, device, ScalarType.Int16);
    }
}
