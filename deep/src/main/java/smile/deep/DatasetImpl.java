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
package smile.deep;

import java.util.Arrays;
import java.util.Iterator;
import smile.deep.tensor.Index;
import smile.deep.tensor.Tensor;
import smile.math.MathEx;

/**
 * A dataset of arrays.
 *
 * @author Haifeng Li
 */
class DatasetImpl implements Dataset {
    private final Tensor data;
    private final Tensor target;
    private final int size;
    private final int batch;

    /**
     * Constructor.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     */
    public DatasetImpl(float[][] data, int[] target, int batch) {
        int n = data.length;
        int p = data[0].length;

        float[] x = new float[n * p];
        long[] y = new long[n];
        for (int i = 0; i < n; i++) {
            y[i] = target[i];
            float[] xi = data[i];
            System.arraycopy(xi, 0, x, i * p, p);
        }

        this.data = Tensor.of(x, n, p);
        this.target = Tensor.of(y, n);
        this.size = n;
        this.batch = batch;
    }

    /**
     * Constructor.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     */
    public DatasetImpl(double[][] data, int[] target, int batch) {
        int n = data.length;
        int p = data[0].length;

        float[] x = new float[n * p];
        long[] y = new long[n];
        for (int i = 0; i < n; i++) {
            y[i] = target[i];
            double[] xi = data[i];
            for (int j = 0; j < p; j++) {
                x[i * p + j] = (float) xi[j];
            }
        }

        this.data = Tensor.of(x, n, p);
        this.target = Tensor.of(y, n);
        this.size = n;
        this.batch = batch;
    }

    /**
     * Constructor.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     */
    public DatasetImpl(float[][] data, float[] target, int batch) {
        int n = data.length;
        int p = data[0].length;

        float[] x = new float[n * p];
        for (int i = 0; i < n; i++) {
            float[] xi = data[i];
            System.arraycopy(xi, 0, x, i * p, p);
        }

        this.data = Tensor.of(x, n, p);
        this.target = Tensor.of(target, n);
        this.size = n;
        this.batch = batch;
    }

    /**
     * Constructor.
     * @param data the data.
     * @param target the target.
     * @param batch the mini-batch size.
     */
    public DatasetImpl(double[][] data, double[] target, int batch) {
        int n = data.length;
        int p = data[0].length;

        float[] x = new float[n * p];
        for (int i = 0; i < n; i++) {
            double[] xi = data[i];
            for (int j = 0; j < p; j++) {
                x[i * p + j] = (float) xi[j];
            }
        }

        this.data = Tensor.of(x, n, p);
        this.target = Tensor.of(target, n);
        this.size = n;
        this.batch = batch;
    }

    @Override
    public void close() {
        data.close();
        target.close();
    }

    @Override
    public long size() {
    return size;
}

    @Override
    public Iterator<SampleBatch> iterator() {
        return new Iterator<>() {
            final int[] permutation = MathEx.permutate(size);
            int[] index = new int[batch];
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < size;
            }

            @Override
            public SampleBatch next() {
                int j = 0;
                for (; j < batch && i < size; j++, i++) {
                    index[j] = permutation[i];
                }

                if (j < batch) {
                    index = Arrays.copyOf(index, j);
                }

                var idx = Index.of(index);
                return new SampleBatch(data.get(idx), target.get(idx));
            }
        };
    }
}
