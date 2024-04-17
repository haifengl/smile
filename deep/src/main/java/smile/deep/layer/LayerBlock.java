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

import org.bytedeco.pytorch.Module;

/**
 * A block is combinations of one or more layers. Blocks form the basis of
 * more complex network designs. LayerBlock allows treating the whole
 * container as a single layer, such that performing a transformation on
 * the LayerBlock applies to each of the layers it contains (which are each
 * a registered submodule of the block).
 *
 * @author Haifeng Li
 */
public abstract class LayerBlock implements Layer {
    final Module module;

    /**
     * Constructor.
     */
    public LayerBlock() {
        this(new Module());
    }

    /**
     * Constructor.
     * @param name the module name.
     */
    public LayerBlock(String name) {
        this(new Module(name));
    }

    /**
     * Constructor.
     * @param module a module.
     */
    public LayerBlock(Module module) {
        this.module = module;
    }

    /**
     * Returns true if the layer is in training mode.
     * @return true if the layer is in training mode.
     */
    public boolean isTraining() {
        return module.is_training();
    }

    /**
     * Returns the PyTorch Module object.
     * @return the PyTorch Module object.
     */
    public Module asTorch() {
        return module;
    }

    @Override
    public void register(String name, Module parent) {
        parent.register_module(name, module);
    }

    @Override
    public String toString() {
        return toString(asTorch(), new StringBuilder(), 0);
    }

    /**
     * Returns the string representation of a module.
     * @param module the module.
     * @param sb string builder.
     * @param indent indent space.
     * @return the string representation.
     */
    private String toString(Module module, StringBuilder sb, int indent) {
        sb.append(module.name().getString());
        var children = module.named_children();
        if (children.size() > 0) {
            sb.append('(');
            sb.append(System.lineSeparator());

            var keys = children.keys();
            for (int i = 0; i < keys.size(); i++) {
                var key = keys.get(i);
                var child = children.get(key);

                for (int j = 0; j < indent + 2; j++) sb.append(' ');
                sb.append(String.format("(%s): ", key.getString()));
                toString(child, sb, indent + 2);
                sb.append(System.lineSeparator());
            }

            for (int j = 0; j < indent; j++) sb.append(' ');
            sb.append(')');
        }
        return sb.toString();
    }

    /**
     * Adds a sub-layer.
     * @param name the name of sub-layer.
     * @param layer the sub-layer.
     * @return this object.
     */
    public LayerBlock add(String name, Layer layer) {
        layer.register(name, module);
        return this;
    }

    /**
     * Adds a sub-layer.
     * @param name the name of sub-layer.
     * @param layer the sub-layer.
     * @return this object.
     */
    public LayerBlock add(String name, Module layer) {
        module.register_module(name, layer);
        return this;
    }

    /**
     * Creates a sequential layer block.
     * @param layers the neural network layers.
     * @return the sequential layer block.
     */
    static SequentialBlock sequential(Layer... layers) {
        return new SequentialBlock(layers);
    }
}
