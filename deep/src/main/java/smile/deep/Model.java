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

import org.bytedeco.pytorch.InputArchive;
import org.bytedeco.pytorch.OutputArchive;
import org.bytedeco.pytorch.Module;

/**
 * A deep learning model.
 *
 * @author Haifeng Li
 */
public class Model {
    private Module net;

    /**
     * Constructor.
     * @param net the neural network module.
     */
    private Model(Module net) {
        this.net = net;
    }
/*
    public Tensor forward(Tensor x) {
        return new Tensor(net.forward(x.value));
    }
*/
    /**
     * Loads a model from checkpoint file.
     * @param path the checkpoint file path.
     * @return the model.
     */
    public static Model load(String path) {
        InputArchive archive = new InputArchive();
        archive.load_from(path);
        Module net = new Module();
        net.load(archive);
        return new Model(net);
    }

    /**
     * Serialize the model as a checkpoint.
     * @param path the checkpoint file path.
     */
    public void save(String path) {
        OutputArchive archive = new OutputArchive();
        net.save(archive);
        archive.save_to(path);
    }
}
