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
import smile.data.vector.BaseVector;
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

    /**
     * Returns a dataset.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     * @return the dataset.
     */
    static Dataset of(double[][] data, int[] target, int batch) {
        final int n = data.length;
        final int p = data[0].length;

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
                    float[] x = new float[batch * p];
                    long[] y = new long[batch];

                    @Override
                    public boolean hasNext() {
                        return i < n;
                    }

                    @Override
                    public Sample next() {
                        int j = 0;
                        for (; j < batch && i < n; j++, i++) {
                            int k = permutation[i];
                            y[j] = target[k];
                            double[] xk = data[k];
                            for (int l = 0; l < p; l++) {
                                x[j*p + l] = (float) xk[l];
                            }
                        }

                        if (i == n) {
                            return new Sample(Tensor.of(Arrays.copyOf(x, j*p), j, p), Tensor.of(Arrays.copyOf(y, j), j));
                        } else {
                            return new Sample(Tensor.of(x, j, p), Tensor.of(y, j));
                        }
                    }
                };
            }
        };
    }

    /**
     * Returns a dataset.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     * @return the dataset.
     */
    static Dataset of(double[][] data, double[] target, int batch) {
        final int n = data.length;
        final int p = data[0].length;

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
                    float[] x = new float[batch * p];
                    float[] y = new float[batch];

                    @Override
                    public boolean hasNext() {
                        return i < n;
                    }

                    @Override
                    public Sample next() {
                        int j = 0;
                        for (; j < batch && i < n; j++, i++) {
                            int k = permutation[i];
                            y[j] = (float) target[k];
                            double[] xk = data[k];
                            for (int l = 0; l < p; l++) {
                                x[j*p + l] = (float) xk[l];
                            }
                        }

                        if (i == n) {
                            return new Sample(Tensor.of(Arrays.copyOf(x, j*p), j, p), Tensor.of(Arrays.copyOf(y, j), j));
                        } else {
                            return new Sample(Tensor.of(x, j, p), Tensor.of(y, j));
                        }
                    }
                };
            }
        };
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
        final BaseVector y = formula.y(df);
        if (y.field().type.isIntegral()) {
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
