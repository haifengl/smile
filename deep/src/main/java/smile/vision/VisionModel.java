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
package smile.vision;

import java.awt.image.BufferedImage;
import smile.deep.Model;
import smile.deep.layer.LayerBlock;
import smile.deep.tensor.Tensor;
import smile.vision.transform.Transform;

/**
 * The computer vision models.
 *
 * @author Haifeng Li
 */
public class VisionModel extends Model {
    /** Image transform. */
    final Transform transform;

    /**
     * Constructor.
     * @param net the neural network.
     * @param transform the image transform.
     */
    public VisionModel(LayerBlock net, Transform transform) {
        super(net);
        this.transform = transform;
    }

    /**
     * Forward propagation (or forward pass) through the model.
     *
     * @param images the input images.
     * @return the output tensor.
     */
    public Tensor forward(BufferedImage... images) {
        try (var input = transform.forward(images)) {
            return forward(input);
        }
    }

    /**
     * Returns the associated image transform.
     * @return the associated image transform.
     */
    public Transform transform() {
        return transform;
    }
}
