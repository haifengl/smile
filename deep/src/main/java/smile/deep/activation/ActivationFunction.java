/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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

package smile.deep.activation;

import java.io.Serializable;
import java.util.function.Function;
import org.bytedeco.pytorch.Module;
import smile.deep.layer.Layer;
import smile.deep.tensor.Tensor;

/**
 * The non-linear activation function.
 *
 * @author Haifeng Li
 */
public abstract class ActivationFunction implements
        Layer, Serializable,
        Function<org.bytedeco.pytorch.Tensor, org.bytedeco.pytorch.Tensor> {

    private final Module module;
    /** The function name. */
    private final String name;
    /** True if the operation executes in-place. */
    private final boolean inplace;

    /**
     * Constructor.
     * @param name the function name.
     * @param inplace true if the operation executes in-place.
     */
    public ActivationFunction(String name, boolean inplace) {
        this.module = new Module(name);
        this.name = name;
        this.inplace = inplace;
    }

    /**
     * Returns the name of activation function.
     * @return the name of activation function.
     */
    public String name() {
        return name;
    }

    /**
     * Returns true if the operation executes in-place.
     * @return true if the operation executes in-place.
     */
    public boolean isInplace() {
        return inplace;
    }

    /**
     * Applies this function to the given argument.
     * @param x a tensor.
     * @return the output tensor.
     */
    public Tensor apply(Tensor x) {
        return new Tensor(apply(x.asTorch()));
    }

    @Override
    public void register(String name, Module parent) {
        parent.register_module(name, module);
    }

    @Override
    public Tensor forward(Tensor x) {
        return apply(x);
    }
}
