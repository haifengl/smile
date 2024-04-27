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

import org.bytedeco.pytorch.InputArchive;
import org.bytedeco.pytorch.Module;
import org.bytedeco.pytorch.OutputArchive;
import smile.deep.Model;
import smile.deep.tensor.Device;

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
    protected final Module module;

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

    @Override
    public Module asTorch() {
        return module;
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

                sb.append(" ".repeat(indent + 2));
                sb.append(String.format("(%s): ", key.getString()));
                toString(child, sb, indent + 2);
                sb.append(System.lineSeparator());
            }

            sb.append(" ".repeat(indent));
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
        return add(name, layer.asTorch());
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
     * Returns true if the layer block is in training mode.
     * @return true if the layer block is in training mode.
     */
    public boolean isTraining() {
        return module.is_training();
    }

    /**
     * Sets the layer block in the training mode.
     */
    public void train() {
        module.train(true);
    }

    /**
     * Sets the layer block in the evaluation/inference mode.
     */
    public void eval() {
        module.eval();
    }

    /**
     * Moves the layer block to a device.
     * @param device the compute device.
     */
    public void to(Device device) {
        module.to(device.asTorch(), true);
    }

    /**
     * Loads a checkpoint.
     * @param path the checkpoint file path.
     */
    public void load(String path) {
        InputArchive archive = new InputArchive();
        archive.load_from(path);
        module.load(archive);
    }

    /**
     * Serialize the layer block as a checkpoint.
     * @param path the checkpoint file path.
     */
    public void save(String path) {
        OutputArchive archive = new OutputArchive();
        module.save(archive);
        archive.save_to(path);
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
