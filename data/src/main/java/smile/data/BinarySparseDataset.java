/*******************************************************************************
 * Copyright (c) 2010 Haifeng Li
 *   
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package smile.data;

import java.util.Arrays;
import smile.math.Math;
import smile.math.matrix.SparseMatrix;

/**
 * Binary sparse dataset. Each item is stored as an integer array, which
 * are the indices of nonzero elements in ascending order.
 *
 * @author Haifeng Li
 */
public class BinarySparseDataset extends Dataset<int[]> {

    /**
     * The number of nonzero entries.
     */
    private int n;
    /**
     * The number of columns.
     */
    private int numColumns;
    /**
     * The number of nonzero entries in each column.
     */
    private int[] colSize;

    /**
     * Constructor.
     */
    public BinarySparseDataset() {
        this("Binary Sparse Dataset");
    }
    
    /**
     * Constructor.
     * @param name the name of dataset.
     */
    public BinarySparseDataset(String name) {
        super(name);
        numColumns = 0;
        colSize = new int[100];
    }

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param response the attribute type of response variable.
     */
    public BinarySparseDataset(String name, Attribute response) {
        super(name, response);
        numColumns = 0;
        colSize = new int[100];
    }

    /**
     * Constructor.
     * @param ncols the number of columns in the matrix.
     */
    public BinarySparseDataset(int ncols) {
        numColumns = ncols;
        colSize = new int[ncols];
    }

    /**
     * Returns the number of columns.
     */
    public int ncols() {
        return numColumns;
    }

    /**
     * Add a datum item into the dataset.
     * @param datum a datum item. The indices of nonzero elements will be sorted
     * into ascending order.
     */
    @Override
    public Datum<int[]> add(Datum<int[]> datum) {
        int[] x = datum.x;
        
        for (int xi : x) {
            if (xi < 0) {
                throw new IllegalArgumentException("Negative index of nonzero element: " + xi);
            }
        }
        
        Arrays.sort(x);
        for (int i = 1; i < x.length; i++) {
            if (x[i] == x[i-1]) {
                throw new IllegalArgumentException("Duplicated indices of nonzero elements: " + x[i]);
            }
        }
        
        n += x.length;
        
        int max = Math.max(x);
        if (numColumns <= max) {
            numColumns = max + 1;
            if (numColumns > colSize.length) {
                int[] size = new int[3 * numColumns / 2];
                System.arraycopy(colSize, 0, size, 0, colSize.length);
                colSize = size;
            }
        }
        
        for (int xi : x) {
            colSize[xi]++;
        }
        
        return super.add(datum);
    }
    
    /**
     * Returns the value at entry (i, j) by binary search.
     * @param i the row index.
     * @param j the column index.
     */
    public int get(int i, int j) {
        if (i < 0 || i >= size()) {
            throw new IllegalArgumentException("Invalid index: i = " + i);
        }

        int[] x = get(i).x;
        if (x.length == 0) {
            return 0;
        }
        
        int low = 0;
        int high = x.length - 1;
        int mid = (low + high) / 2;
        
        while (j != x[mid] && low <= high) {
            mid = (low + high) / 2;
            if (j < x[mid]) 
                high = mid - 1;
            else
                low = mid + 1;
        }
        
        if (j == x[mid]) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Convert into Harwell-Boeing column-compressed sparse matrix format.
     */
    public SparseMatrix toSparseMatrix() {
        int[] pos = new int[numColumns];
        int[] colIndex = new int[numColumns + 1];
        for (int i = 0; i < numColumns; i++) {
            colIndex[i + 1] = colIndex[i] + colSize[i];
        }

        int nrows = size();
        int[] rowIndex = new int[n];
        double[] x = new double[n];

        for (int i = 0; i < nrows; i++) {
            for (int j : get(i).x) {
                int k = colIndex[j] + pos[j];

                rowIndex[k] = i;
                x[k] = 1;
                pos[j]++;
            }
        }

        return new SparseMatrix(nrows, numColumns, x, rowIndex, colIndex);
    }
}
