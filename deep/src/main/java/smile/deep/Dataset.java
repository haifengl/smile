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

import java.util.Arrays;
import java.util.Iterator;
import org.bytedeco.pytorch.*;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.math.MathEx;

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

    static Dataset of(double[][] x, int[] y, int batch) {
        final int n = x.length;
        final int p = x[0].length;

        return new Dataset() {
            @Override
            public long size() {
                return n;
            }

            @Override
            public Iterator<Sample> iterator() {
                final int[] permutation = MathEx.permutate(n);
                return new Iterator<Sample>() {
                    int i = 0;
                    float[] batchx = new float[batch * p];
                    long[] batchy = new long[batch];

                    @Override
                    public boolean hasNext() {
                        return i < n;
                    }

                    @Override
                    public Sample next() {
                        int j = 0;
                        for (; j < batch && i < n; j++, i++) {
                            int k = permutation[i];
                            for (int l = 0; l < p; l++) {
                                batchx[j*p + l] = (float) x[k][l];
                            }
                            batchy[j] = y[k];
                        }

                        if (i == n) {
                            return new Sample(Tensor.of(Arrays.copyOf(batchx, j*p), j, p), Tensor.of(Arrays.copyOf(batchy, j), j));
                        } else {
                            return new Sample(Tensor.of(batchx, j, p), Tensor.of(batchy, j));
                        }
                    }
                };
            }
        };
    }

    static Dataset of(DataFrame df, Formula formula, int batch) {
        final double[][] x = formula.x(df).toArray();
        final int[] y = formula.y(df).toIntArray();
        return of(x, y, batch);
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
