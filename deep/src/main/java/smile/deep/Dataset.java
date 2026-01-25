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
package smile.deep;

import org.bytedeco.pytorch.*;
import smile.data.DataFrame;
import smile.data.formula.Formula;

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
     * @param path the data folder path.
     * @param trainMode load training or test data.
     * @param batch the mini-batch size.
     * @return the MNIST dataset.
     */
    static Dataset mnist(String path, boolean trainMode, int batch) {
        return new Dataset() {
            final int mode = trainMode ? MNIST.Mode.kTrain.value : MNIST.Mode.kTest.value;
            final MNISTMapDataset dataset = new MNIST(path, mode).map(new ExampleStack());
            final MNISTRandomDataLoader loader = new MNISTRandomDataLoader(
                    dataset, new RandomSampler(dataset.size().get()),
                    new DataLoaderOptions(batch));

            @Override
            public long size() {
                return dataset.size().get();
            }

            @Override
            public DataSampler iterator() {
                return new DataSampler(loader.begin(), loader.end());
            }

            @Override
            public void close() {
                dataset.close();
            }
        };
    }
}
