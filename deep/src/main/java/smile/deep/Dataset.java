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

import org.bytedeco.pytorch.*;

/**
 * A dataset consists of data and an associated target (label).
 *
 * @author Haifeng Li
 */
public interface Dataset extends Iterable<Sample> {
    /**
     * Returns the size of dataset.
     * @return the size of dataset.
     */
    long size();

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
        int mode = trainMode ? MNIST.Mode.kTrain.value : MNIST.Mode.kTest.value;
        MNISTMapDataset dataset = new MNIST(path, mode).map(new ExampleStack());
        MNISTRandomDataLoader loader = new MNISTRandomDataLoader(
                dataset, new RandomSampler(dataset.size().get()),
                new DataLoaderOptions(batch));

        return new Dataset() {
            @Override
            public long size() {
                return dataset.size().get();
            }

            @Override
            public DataSampler iterator() {
                return new DataSampler(loader.begin(), loader.end());
            }
        };
    }
}
