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
 * along with SMILE. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.vision.transform;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import smile.deep.tensor.Tensor;

/**
 * Image transform for classification. The images are resized using scaling
 * hints, followed by a central crop. Finally, the values are first rescaled
 * to [0.0, 1.0] and then normalized.
 *
 * @author Haifeng Li
 */
class ImageClassification implements Transform {
    private final int cropSize;
    private final int resizeSize;
    private final float[] mean;
    private final float[] std;
    private final int hints;

    /**
     * Constructor.
     * @param cropSize the crop size.
     * @param resizeSize the scaling size.
     */
    public ImageClassification(int cropSize, int resizeSize) {
        this(cropSize, resizeSize, DEFAULT_MEAN, DEFAULT_STD, Image.SCALE_FAST);
    }

    /**
     * Constructor.
     * @param cropSize the crop size.
     * @param resizeSize the scaling size.
     * @param mean the normalization mean.
     * @param std the normalization standard deviation.
     * @param hints the scaling hints.
     */
    public ImageClassification(int cropSize, int resizeSize, float[] mean, float[] std, int hints) {
        this.cropSize = cropSize;
        this.resizeSize = resizeSize;
        this.mean = mean;
        this.std = std;
        this.hints = hints;
    }

    @Override
    public Tensor forward(BufferedImage... images) {
        BufferedImage[] output = new BufferedImage[images.length];
        for (int i = 0; i < images.length; i++) {
            BufferedImage image = resize(images[i], resizeSize, hints);
            output[i] = crop(image, cropSize, true);
        }
        return toTensor(mean, std, output);
    }

    @Override
    public String toString() {
        return String.format("""
                ImageClassification(
                    cropSize = %d,
                    resizeSize = %d,
                    mean = %s,
                    std = %s,
                    hints = %d
                )""", cropSize, resizeSize, Arrays.toString(mean),
                Arrays.toString(std), hints);
    }
}
