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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import smile.math.Math;
import smile.math.SparseArray;
import smile.math.matrix.SparseMatrix;

/**
 * List of Lists sparse matrix format. LIL stores one list per row,
 * where each entry stores a column index and value. Typically, these
 * entries are kept sorted by column index for faster lookup.
 * This format is good for incremental matrix construction.
 * <p>
 * LIL is typically used to construct the matrix. Once the matrix is
 * constructed, it is typically converted to a format, such as Harwell-Boeing
 * column-compressed sparse matrix format, which is more efficient for matrix
 * operations.
 *
 * @author Haifeng Li
 */
public class SparseDataset implements Iterable<Datum<SparseArray>> {

    /**
     * The name of dataset.
     */
    private String name;
    /**
     * The optional detailed description of dataset.
     */
    private String description;
    /**
     * The attribute property of response variable. null means no response variable.
     */
    private Attribute response = null;
    /**
     * The data objects.
     */
    private List<Datum<SparseArray>> data = new ArrayList<>();
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
    public SparseDataset() {
        this("Sparse Dataset");
    }

    /**
     * Constructor.
     * @param name the name of dataset.
     */
    public SparseDataset(String name) {
        this(name, null);
    }

    /**
     * Constructor.
     * @param response the attribute type of response variable.
     */
    public SparseDataset(Attribute response) {
        this("SparseDataset", response);
    }

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param response the attribute type of response variable.
     */
    public SparseDataset(String name, Attribute response) {
        this.name = name;
        this.response = response;
        numColumns = 0;
        colSize = new int[100];
    }

    /**
     * Constructor.
     * @param ncols the number of columns in the matrix.
     */
    public SparseDataset(int ncols) {
        numColumns = ncols;
        colSize = new int[ncols];
    }

    /**
     * Constructor.
     * @param ncols the number of columns in the matrix.
     * @param response the attribute type of response variable.
     */
    public SparseDataset(int ncols, Attribute response) {
        this.numColumns = ncols;
        this.colSize = new int[ncols];
        this.response = response; 
    }

    /**
     * Returns the dataset name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the dataset name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Sets the detailed dataset description.
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the detailed dataset description.
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the attribute of the response variable. null means no response
     * variable in this dataset.
     * @return the attribute of the response variable. null means no response
     * variable in this dataset.
     */
    public Attribute response() {
        return response;
    }
    
    /**
     * Returns the size of dataset that is the number of rows.
     */
    public int size() {
        return data.size();
    }
    
    /**
     * Returns the number of columns.
     */
    public int ncols() {
        return numColumns;
    }

    /**
     * Returns the number of nonzero entries.
     */
    public int length() {
        return n;
    }
    
    /**
     * Set the class label of a datum. If the index
     * exceeds the current matrix size, the matrix will resize itself.
     * @param i the row index of entry.
     * @param y the class label or real-valued response of the datum.
     */
    public void set(int i, int y) {
        if (response == null) {
            throw new IllegalArgumentException("The dataset has no response values.");            
        }
        
        if (response.getType() != Attribute.Type.NOMINAL) {
            throw new IllegalArgumentException("The response variable is not nominal.");
        }
        
        if (i < 0) {
            throw new IllegalArgumentException("Invalid index: i = " + i);
        }

        int nrows = size();
        if (i >= nrows) {
            for (int k = nrows; k <= i; k++) {
                data.add(new Datum<>(new SparseArray()));
            }
        }

        get(i).y = y;
    }

    /**
     * Set the real-valued response of a datum. If the index
     * exceeds the current matrix size, the matrix will resize itself.
     * @param i the row index of entry.
     * @param y the class label or real-valued response of the datum.
     */
    public void set(int i, double y) {
        if (response == null) {
            throw new IllegalArgumentException("The dataset has no response values.");            
        }
        
        if (response.getType() != Attribute.Type.NUMERIC) {
            throw new IllegalArgumentException("The response variable is not numeric.");
        }
        
        if (i < 0) {
            throw new IllegalArgumentException("Invalid index: i = " + i);
        }

        int nrows = size();
        if (i >= nrows) {
            for (int k = nrows; k <= i; k++) {
                data.add(new Datum<>(new SparseArray()));
            }
        }

        get(i).y = y;
    }

    /**
     * Set the class label of real-valued response of a datum. If the index
     * exceeds the current matrix size, the matrix will resize itself.
     * @param i the row index of entry.
     * @param y the class label or real-valued response of the datum.
     * @param weight the optional weight of the datum.
     */
    public void set(int i, double y, double weight) {
        if (i < 0) {
            throw new IllegalArgumentException("Invalid index: i = " + i);
        }

        int nrows = size();
        if (i >= nrows) {
            for (int k = nrows; k <= i; k++) {
                data.add(new Datum<>(new SparseArray()));
            }
        }

        Datum<SparseArray> datum = get(i);
        datum.y = y;
        datum.weight = weight;
    }

    /**
     * Set a nonzero entry into the matrix. If the index exceeds the current
     * matrix size, the matrix will resize itself.
     * @param i the row index of entry.
     * @param j the column index of entry.
     * @param x the value of entry.
     */
    public void set(int i, int j, double x) {
        if (i < 0 || j < 0) {
            throw new IllegalArgumentException("Invalid index: i = " + i + " j = " + j);
        }

        int nrows = size();
        if (i >= nrows) {
            for (int k = nrows; k <= i; k++) {
                data.add(new Datum<>(new SparseArray()));
            }
        }

        if (j >= ncols()) {
            numColumns = j + 1;
            if (numColumns > colSize.length) {
                int[] size = new int[3 * numColumns / 2];
                System.arraycopy(colSize, 0, size, 0, colSize.length);
                colSize = size;
            }
        }

        if (get(i).x.set(j, x)) {
            colSize[j]++;
            n++;            
        }
    }

    /**
     * Returns the element at the specified position in this dataset.
     * @param i the index of the element to be returned.
     */
    public Datum<SparseArray> get(int i) {
        return data.get(i);
    }
    
    /**
     * Returns the value at entry (i, j).
     * @param i the row index.
     * @param j the column index.
     */
    public double get(int i, int j) {
        if (i < 0 || i >= size() || j < 0 || j >= ncols()) {
            throw new IllegalArgumentException("Invalid index: i = " + i + " j = " + j);
        }

        for (SparseArray.Entry e : get(i).x) {
            if (e.i == j) {
                return e.x;
            }
        }

        return 0.0;
    }

    /**
     * Removes the element at the specified position in this dataset.
     * @param i the index of the element to be removed.
     * @return the element previously at the specified position.
     */
    public Datum<SparseArray> remove(int i) {
        Datum<SparseArray> datum = data.remove(i);

        n -= datum.x.size();
        for (SparseArray.Entry item : datum.x) {
            colSize[item.i]--;
        }
        
        return datum;
    }

    /**
     * Unitize each row so that L2 norm of x = 1.
     */
    public void unitize() {
        for (Datum<SparseArray> row : this) {
            double sum = 0.0;

            for (SparseArray.Entry e : row.x) {
                sum += Math.sqr(e.x);
            }

            sum = Math.sqrt(sum);

            for (SparseArray.Entry e : row.x) {
                e.x /= sum;
            }
        }
    }

    /**
     * Unitize each row so that L1 norm of x is 1.
     */
    public void unitize1() {
        for (Datum<SparseArray> row : this) {
            double sum = 0.0;

            for (SparseArray.Entry e : row.x) {
                sum += Math.abs(e.x);
            }

            for (SparseArray.Entry e : row.x) {
                e.x /= sum;
            }
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
            for (SparseArray.Entry e : get(i).x) {
                int j = e.i;
                int k = colIndex[j] + pos[j];

                rowIndex[k] = i;
                x[k] = e.x;
                pos[j]++;
            }
        }

        return new SparseMatrix(nrows, numColumns, x, rowIndex, colIndex);
    }
    
    /**
     * Returns an iterator over the elements in this dataset in proper sequence. 
     * @return an iterator over the elements in this dataset in proper sequence
     */
    @Override
    public Iterator<Datum<SparseArray>> iterator() {
        return new Iterator<Datum<SparseArray>>() {

            /**
             * Current position.
             */
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < data.size();
            }

            @Override
            public Datum<SparseArray> next() {
                return get(i++);
            }

            @Override
            public void remove() {
                SparseDataset.this.remove(i);
            }
        };
    }    
    
    /**
     * Returns a dense two-dimensional array containing the whole matrix in
     * this dataset in proper sequence. 
     * 
     * @return a dense two-dimensional array containing the whole matrix in
     * this dataset in proper sequence. 
     */
    public double[][] toArray() {
        int m = data.size();
        double[][] a = new double[m][ncols()];
        
        for (int i = 0; i < m; i++) {
            for (SparseArray.Entry item : get(i).x) {
                a[i][item.i] = item.x;
            }
        }
        
        return a;
    }
    
    /**
     * Returns an array containing all of the elements in this dataset in
     * proper sequence (from first to last element); the runtime type of the
     * returned array is that of the specified array. If the dataset fits in
     * the specified array, it is returned therein. Otherwise, a new array
     * is allocated with the runtime type of the specified array and the size
     * of this dataset.
     * <p>
     * If the dataset fits in the specified array with room to spare (i.e., the
     * array has more elements than the dataset), the element in the array
     * immediately following the end of the dataset is set to null. 
     * 
     * @param a the array into which the elements of this dataset are to be
     * stored, if it is big enough; otherwise, a new array of the same runtime
     * type is allocated for this purpose. 
     * @return an array containing the elements of this dataset.
     */
    public SparseArray[] toArray(SparseArray[] a) {
        int m = data.size();
        if (a.length < m) {
            a = new SparseArray[m];
        }
        
        for (int i = 0; i < m; i++) {
            a[i] = get(i).x;
        }
        
        for (int i = m; i < a.length; i++) {
            a[i] = null;
        }
        
        return a;
    }
    
    /**
     * Returns an array containing the class labels of the elements in this
     * dataset in proper sequence (from first to last element). Unknown labels
     * will be saved as Integer.MIN_VALUE. If the dataset fits in the specified
     * array, it is returned therein. Otherwise, a new array is allocated with
     * the size of this dataset.
     * <p>
     * If the dataset fits in the specified array with room to spare (i.e., the
     * array has more elements than the dataset), the element in the array
     * immediately following the end of the dataset is set to Integer.MIN_VALUE. 
     * 
     * @param a the array into which the class labels of this dataset are to be
     * stored, if it is big enough; otherwise, a new array is allocated for
     * this purpose. 
     * @return an array containing the class labels of this dataset.
     */
    public int[] toArray(int[] a) {
        if (response == null) {
            throw new IllegalArgumentException("The dataset has no response values.");            
        }
        
        if (response.getType() != Attribute.Type.NOMINAL) {
            throw new IllegalArgumentException("The response variable is not nominal.");
        }
        
        int m = data.size();
        if (a.length < m) {
            a = new int[m];
        }
        
        for (int i = 0; i < m; i++) {
            Datum<SparseArray> datum = get(i);
            if (Double.isNaN(datum.y)) {
                a[i] = Integer.MIN_VALUE;
            } else {
                a[i] = (int) get(i).y;
            }
        }
        
        for (int i = m; i < a.length; i++) {
            a[i] = Integer.MIN_VALUE;
        }
        
        return a;
    }

    /**
     * Returns an array containing the response variable of the elements in this
     * dataset in proper sequence (from first to last element). If the dataset
     * fits in the specified array, it is returned therein. Otherwise, a new array
     * is allocated with the size of this dataset.
     * <p>
     * If the dataset fits in the specified array with room to spare (i.e., the
     * array has more elements than the dataset), the element in the array
     * immediately following the end of the dataset is set to Double.NaN.
     * 
     * @param a the array into which the response variable of this dataset are
     * to be stored, if it is big enough; otherwise, a new array is allocated
     * for this purpose. 
     * @return an array containing the response variable of this dataset.
     */
    public double[] toArray(double[] a) {
        if (response == null) {
            throw new IllegalArgumentException("The dataset has no response values.");            
        }
        
        if (response.getType() != Attribute.Type.NUMERIC) {
            throw new IllegalArgumentException("The response variable is not numeric.");
        }
        
        int m = data.size();
        if (a.length < m) {
            a = new double[m];
        }
        
        for (int i = 0; i < m; i++) {
            a[i] = get(i).y;
        }
        
        for (int i = m; i < a.length; i++) {
            a[i] = Double.NaN;
        }
        
        return a;
    }
}
