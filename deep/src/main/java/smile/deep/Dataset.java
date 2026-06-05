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
package smile.deep;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.deep.tensor.Tensor;

/**
 * A dataset consists of data and an associated target (label)
 * and can be iterated in mini-batches.
 *
 * @author Haifeng Li
 */
public interface Dataset extends Iterable<SampleBatch>, AutoCloseable {
    /**
     * Returns the size of dataset.
     * @return the size of dataset.
     */
    long size();

    /**
     * Creates a dataset of numeric arrays.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     * @return the dataset.
     */
    static Dataset of(float[][] data, int[] target, int batch) {
        return new DatasetImpl(data, target, batch);
    }

    /**
     * Creates a dataset of numeric arrays.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     * @return the dataset.
     */
    static Dataset of(float[][] data, float[] target, int batch) {
        return new DatasetImpl(data, target, batch);
    }

    /**
     * Creates a dataset of numeric arrays.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     * @return the dataset.
     */
    static Dataset of(double[][] data, int[] target, int batch) {
        return new DatasetImpl(data, target, batch);
    }

    /**
     * Creates a dataset of numeric arrays.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     * @return the dataset.
     */
    static Dataset of(double[][] data, double[] target, int batch) {
        return new DatasetImpl(data, target, batch);
    }

    /**
     * Returns a dataset.
     * @param formula a symbolic description of the model to be fitted.
     * @param df the data frame of the explanatory and response variables.
     * @param batch the mini-batch size.
     * @return the dataset.
     */
    static Dataset of(Formula formula, DataFrame df, int batch) {
        final double[][] x = formula.x(df).toArray();
        final var y = formula.y(df);
        if (y.field().dtype().isIntegral()) {
            return of(x, y.toIntArray(), batch);
        } else {
            return of(x, y.toDoubleArray(), batch);
        }
    }

    /**
     * MNIST contains 70,000 images of handwritten digits: 60,000 for
     * training and 10,000 for testing. The images are grayscale,
     * 28x28 pixels, and centered.
     *
     * <p>The images and labels are read directly from the standard
     * uncompressed IDX files (e.g. {@code train-images-idx3-ubyte}) found in
     * {@code path}. Pixel values are scaled to {@code [0, 1]} and the data
     * tensor has shape {@code [N, 1, 28, 28]}.
     *
     * @param path the data folder path.
     * @param trainMode load training or test data.
     * @param batch the mini-batch size.
     * @return the MNIST dataset.
     */
    static Dataset mnist(String path, boolean trainMode, int batch) {
        String prefix = trainMode ? "train" : "t10k";
        try {
            float[] images = readImages(Path.of(path, prefix + "-images-idx3-ubyte"));
            long[] labels = readLabels(Path.of(path, prefix + "-labels-idx1-ubyte"));
            int n = labels.length;
            Tensor data = Tensor.of(images, n, 1, 28, 28);
            Tensor target = Tensor.of(labels, n);
            return new DatasetImpl(data, target, batch);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to load MNIST data from " + path, ex);
        }
    }

    /** Reads an IDX3 image file into a flat float array scaled to [0, 1]. */
    private static float[] readImages(Path file) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            in.readInt();                 // magic number (0x00000803)
            int count = in.readInt();
            int rows = in.readInt();
            int cols = in.readInt();
            byte[] bytes = in.readNBytes(count * rows * cols);
            float[] images = new float[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                images[i] = (bytes[i] & 0xFF) / 255.0f;
            }
            return images;
        }
    }

    /** Reads an IDX1 label file into a long array. */
    private static long[] readLabels(Path file) throws IOException {
        try (var in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            in.readInt();                 // magic number (0x00000801)
            int count = in.readInt();
            byte[] bytes = in.readNBytes(count);
            long[] labels = new long[count];
            for (int i = 0; i < count; i++) {
                labels[i] = bytes[i] & 0xFF;
            }
            return labels;
        }
    }
}
