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
package smile.vision.transform;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import smile.deep.tensor.Tensor;

/**
 * Transformation from image to tensor.
 *
 * @author Haifeng Li
 */
public interface Transform {
    /**
     * Transforms images to 4-D tensor with shape [samples, channels, height, width].
     * @param images the input images.
     * @return the 4-D tensor representation of images.
     */
    Tensor forward(BufferedImage... images);

    /**
     * The default mean value of pixel RGB after normalized to [0, 1].
     * Calculated on ImageNet data.
     */
    float[] DEFAULT_MEAN = {0.485f, 0.456f, 0.406f};
    /**
     * The default standard deviation of pixel RGB after normalized to [0, 1].
     * Calculated on ImageNet data.
     */
    float[] DEFAULT_STD = {0.229f, 0.224f, 0.225f};

    /**
     * Resizes an image and keeps the aspect ratio.
     * @param image the input image.
     * @param size the image size of the shorter side.
     * @param hints flags to indicate the type of algorithm to use for
     *             image resampling. See SCALE_DEFAULT, SCALE_FAST,
     *             SCALE_SMOOTH, SCALE_REPLICATE, SCALE_AREA_AVERAGING
     *             of java.awt.Image class.
     * @return the output image.
     * @throws IllegalArgumentException if {@code image} is null or {@code size} is not positive.
     */
    default BufferedImage resize(BufferedImage image, int size, int hints) {
        if (image == null) throw new IllegalArgumentException("image must not be null");
        if (size <= 0) throw new IllegalArgumentException("resize size must be positive: " + size);
        Image resizedImage = image.getHeight() > image.getWidth() ?
                image.getScaledInstance(size, -1, hints) :
                image.getScaledInstance(-1, size, hints);
        BufferedImage output = new BufferedImage(resizedImage.getWidth(null),
                resizedImage.getHeight(null), BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = output.getGraphics();
        g.drawImage(resizedImage, 0, 0, null);
        g.dispose();
        return output;
    }

    /**
     * Crops an image.
     * @param image the input image.
     * @param size the cropped image size.
     * @param deep If false, the returned BufferedImage shares the same data
     *            array as the original image. Otherwise, returns a deep copy.
     * @return the cropped image.
     * @throws IllegalArgumentException if {@code image} is null, dimensions are not positive,
     *         or crop size exceeds the image size.
     */
    default BufferedImage crop(BufferedImage image, int size, boolean deep) {
        return crop(image, size, size, deep);
    }

    /**
     * Crops an image. The returned BufferedImage shares the same data array
     * as the original image.
     * @param image the input image.
     * @param width the cropped image width.
     * @param height the cropped image height.
     * @param deep If false, the returned BufferedImage shares the same data
     *            array as the original image. Otherwise, returns a deep copy.
     * @return the cropped image.
     * @throws IllegalArgumentException if {@code image} is null, dimensions are not positive,
     *         or crop size exceeds the image size.
     */
    default BufferedImage crop(BufferedImage image, int width, int height, boolean deep) {
        if (image == null) throw new IllegalArgumentException("image must not be null");
        if (width <= 0 || height <= 0)
            throw new IllegalArgumentException(String.format(
                    "crop dimensions must be positive: width=%d, height=%d", width, height));
        if (width > image.getWidth() || height > image.getHeight())
            throw new IllegalArgumentException(String.format(
                    "crop size (%dx%d) exceeds image size (%dx%d)",
                    width, height, image.getWidth(), image.getHeight()));
        int x = (image.getWidth() - width) / 2;
        int y = (image.getHeight() - height) / 2;
        BufferedImage output = image.getSubimage(x, y, width, height);

        if (deep) {
            BufferedImage croppedImage = output;
            output = new BufferedImage(croppedImage.getWidth(null),
                    croppedImage.getHeight(null), BufferedImage.TYPE_3BYTE_BGR);
            Graphics g = output.getGraphics();
            g.drawImage(croppedImage, 0, 0, null);
            g.dispose();
        }

        return output;
    }

    /**
     * Returns the tensor with NCHW shape [samples, channels, height, width] of the images.
     * The values of tensor are first rescaled to [0.0, 1.0] and then normalized.
     * @param images the input images that should have same size.
     * @param mean the normalization mean.
     * @param std the normalization standard deviation.
     * @return the output tensor.
     * @throws IllegalArgumentException if {@code images} is null or empty, {@code mean}/{@code std}
     *         do not have exactly 3 elements, any {@code std} element is zero, or the images do not
     *         all have the same dimensions.
     */
    default Tensor toTensor(float[] mean, float[] std, BufferedImage... images) {
        if (images == null || images.length == 0)
            throw new IllegalArgumentException("images must not be null or empty");
        if (mean == null || mean.length != 3)
            throw new IllegalArgumentException("mean must have exactly 3 elements");
        if (std == null || std.length != 3)
            throw new IllegalArgumentException("std must have exactly 3 elements");
        for (int c = 0; c < 3; c++) {
            if (std[c] == 0.0f) throw new IllegalArgumentException("std[" + c + "] must not be zero");
        }
        final int width = images[0].getWidth();
        final int height = images[0].getHeight();
        for (int k = 1; k < images.length; k++) {
            if (images[k].getWidth() != width || images[k].getHeight() != height)
                throw new IllegalArgumentException(String.format(
                        "All images must have the same size. images[0] is %dx%d but images[%d] is %dx%d",
                        width, height, k, images[k].getWidth(), images[k].getHeight()));
        }
        final int length = height * width;
        final int green = length;
        final int blue  = 2 * length;
        float[] result = new float[images.length * length * 3];

        for (int k = 0, offset = 0; k < images.length; k++, offset += 3*length) {
            BufferedImage image = images[k];
            DataBuffer buffer = image.getData().getDataBuffer();

            if (image.getType() == BufferedImage.TYPE_3BYTE_BGR && buffer.getNumBanks() == 1) {
                // faster as it avoids data copying.
                byte[] pixels = ((DataBufferByte) buffer).getData();
                for (int i = 0, j = 0; i < length; i++, j+=3) {
                    result[i + offset]         = ((pixels[j + 2] & 0xff) / 255.0f - mean[0]) / std[0]; // red
                    result[i + offset + green] = ((pixels[j + 1] & 0xff) / 255.0f - mean[1]) / std[1]; // green
                    result[i + offset + blue]  = ((pixels[j]     & 0xff) / 255.0f - mean[2]) / std[2]; // blue
                }
            } else if ((image.getType() == BufferedImage.TYPE_4BYTE_ABGR || image.getType() == BufferedImage.TYPE_4BYTE_ABGR_PRE) && buffer.getNumBanks() == 1) {
                // faster as it avoids data copying.
                byte[] pixels = ((DataBufferByte) buffer).getData();
                for (int i = 0, j = 0; i < length; i++, j+=4) {
                    result[i + offset]         = ((pixels[j + 3] & 0xff) / 255.0f - mean[0]) / std[0]; // red
                    result[i + offset + green] = ((pixels[j + 2] & 0xff) / 255.0f - mean[1]) / std[1]; // green
                    result[i + offset + blue]  = ((pixels[j + 1] & 0xff) / 255.0f - mean[2]) / std[2]; // blue
                }
            } else {
                int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
                for (int i = 0; i < length; i++) {
                    int pixel = pixels[i];
                    result[i + offset]         = (((pixel >> 16) & 0xff) / 255.0f - mean[0]) / std[0]; // red
                    result[i + offset + green] = (((pixel >>  8) & 0xff) / 255.0f - mean[1]) / std[1]; // green
                    result[i + offset + blue]  = (((pixel      ) & 0xff) / 255.0f - mean[2]) / std[2]; // blue
                }
            }
        }

        return Tensor.of(result, images.length, 3, height, width);
    }

    /**
     * Returns a transform for image classification. The images are resized
     * using scaling hints, followed by a central crop. Finally, the values
     * are first rescaled to [0.0, 1.0] and then normalized.
     *
     * @param cropSize the crop size.
     * @param resizeSize the scaling size.
     * @return a transform for image classification.
     */
    static Transform classification(int cropSize, int resizeSize) {
        return new ImageClassification(cropSize, resizeSize);
    }

    /**
     * Returns a transform for image classification. The images are resized
     * using scaling hints, followed by a central crop. Finally, the values
     * are first rescaled to [0.0, 1.0] and then normalized.
     *
     * @param cropSize the crop size.
     * @param resizeSize the scaling size.
     * @param mean the normalization mean.
     * @param std the normalization standard deviation.
     * @param hints the scaling hints.
     * @return a transform for image classification.
     */
    static Transform classification(int cropSize, int resizeSize, float[] mean, float[] std, int hints) {
        return new ImageClassification(cropSize, resizeSize, mean, std, hints);
    }
}
