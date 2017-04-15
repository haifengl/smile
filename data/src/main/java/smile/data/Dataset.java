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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A set of data objects.
 *
 * @param <E> the type of data objects.
 * 
 * @author Haifeng Li
 */
public class Dataset<E> implements Iterable<Datum<E>> {
    /**
     * The name of dataset.
     */
    private String name;
    /**
     * The optional detailed description of dataset.
     */
    private String description = "";
    /**
     * The attribute property of response variable. null means no response variable.
     */
    private Attribute response = null;
    /**
     * The data objects.
     */
    private List<Datum<E>> data = new ArrayList<>();

    /**
     * Constructor.
     */
    public Dataset() {
        this("Dataset");
    }

    /**
     * Constructor.
     * @param name the name of dataset.
     */
    public Dataset(String name) {
        this.name = name;
    }

    /**
     * Constructor.
     * @param response the attribute type of response variable.
     */
    public Dataset(Attribute response) {
        this("Dataset", response);
    }

    /**
     * Constructor.
     * @param name the name of dataset.
     * @param response the attribute type of response variable.
     */
    public Dataset(String name, Attribute response) {
        this.name = name;
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
     * Returns the size of dataset.
     */
    public int size() {
        return data.size();
    }
    
    /**
     * Add a datum item into the dataset.
     * @param x a datum item.
     */
    public void add(Datum<E> x) {
        data.add(x);
    }
    
    /**
     * Add a datum item into the dataset.
     * @param x a datum item.
     */
    public void add(E x) {
        add(new Datum<>(x));
    }

    /**
     * Add a datum item into the dataset.
     * @param x a datum item.
     * @param y the class label of the datum.
     */
    public void add(E x, int y) {
        if (response == null) {
            throw new IllegalArgumentException("The dataset has no response values.");            
        }
        
        if (response.getType() != Attribute.Type.NOMINAL) {
            throw new IllegalArgumentException("The response variable is not nominal.");
        }
        
        add(new Datum<>(x, y));
    }

    /**
     * Add a datum item into the dataset.
     * @param x a datum item.
     * @param y the class label of the datum.
     * @param weight the weight of datum. The particular meaning of weight
     * depends on applications and machine learning algorithms. Although there
     * are on explicit requirements on the weights, in general, they should be
     * positive.
     */
    public void add(E x, int y, double weight) {
        if (response == null) {
            throw new IllegalArgumentException("The dataset has no response values.");            
        }
        
        if (response.getType() != Attribute.Type.NOMINAL) {
            throw new IllegalArgumentException("The response variable is not nominal.");
        }
        
        add(new Datum<>(x, y, weight));
    }

    /**
     * Add a datum item into the dataset.
     * @param x a datum item.
     * @param y the real-valued response for regression.
     */
    public void add(E x, double y) {
        if (response == null) {
            throw new IllegalArgumentException("The dataset has no response values.");            
        }
        
        if (response.getType() != Attribute.Type.NUMERIC) {
            throw new IllegalArgumentException("The response variable is not numeric.");
        }
        
        add(new Datum<>(x, y));
    }

    /**
     * Add a datum item into the dataset.
     * @param x a datum item.
     * @param weight the weight of datum. The particular meaning of weight
     * depends on applications and machine learning algorithms. Although there
     * are on explicit requirements on the weights, in general, they should be
     * positive.
     */
    public void add(E x, double y, double weight) {
        if (response == null) {
            throw new IllegalArgumentException("The dataset has no response values.");            
        }
        
        if (response.getType() != Attribute.Type.NUMERIC) {
            throw new IllegalArgumentException("The response variable is not numeric.");
        }
        
        add(new Datum<>(x, y, weight));
    }

    /**
     * Removes the element at the specified position in this dataset.
     * @param i the index of the element to be removed.
     * @return the element previously at the specified position.
     */
    public Datum<E> remove(int i) {
        return data.remove(i);
    }

    /**
     * Returns the element at the specified position in this dataset.
     * @param i the index of the element to be returned.
     */
    public Datum<E> get(int i) {
        return data.get(i);
    }
    
    /**
     * Returns an iterator over the elements in this dataset in proper sequence. 
     * @return an iterator over the elements in this dataset in proper sequence
     */
    @Override
    public Iterator<Datum<E>> iterator() {
        return new Iterator<Datum<E>>() {

            /**
             * Current position.
             */
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < data.size();
            }

            @Override
            public Datum<E> next() {
                return get(i++);
            }

            @Override
            public void remove() {
                Dataset.this.remove(i);
            }
        };
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
     * @return an array containing the elements of this list.
     */
    @SuppressWarnings("unchecked")
    public E[] toArray(E[] a) {
        int n = data.size();
        if (a.length < n) {
            a = (E[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), n);
        }
        
        for (int i = 0; i < n; i++) {
            a[i] = get(i).x;
        }
        
        for (int i = n; i < a.length; i++) {
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
        
        int n = data.size();
        if (a.length < n) {
            a = new int[n];
        }
        
        for (int i = 0; i < n; i++) {
            Datum<E> datum = get(i);
            if (Double.isNaN(datum.y)) {
                a[i] = Integer.MIN_VALUE;
            } else {
                a[i] = (int) get(i).y;
            }
        }
        
        for (int i = n; i < a.length; i++) {
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
        
        int n = data.size();
        if (a.length < n) {
            a = new double[n];
        }
        
        for (int i = 0; i < n; i++) {
            a[i] = get(i).y;
        }
        
        for (int i = n; i < a.length; i++) {
            a[i] = Double.NaN;
        }
        
        return a;
    }

    /**
     * Returns an array containing the string names of the elements in this
     * dataset in proper sequence (from first to last element). If the dataset
     * fits in the specified array, it is returned therein. Otherwise, a new
     * array is allocated with the size of this dataset.
     * <p>
     * If the dataset fits in the specified array with room to spare (i.e., the
     * array has more elements than the dataset), the element in the array
     * immediately following the end of the dataset is set to null.
     * 
     * @param a the array into which the string names of the elements in this
     * dataset are to be stored, if it is big enough; otherwise, a new array
     * is allocated for this purpose. 
     * @return an array containing the string names of the elements in this dataset.
     */
    public String[] toArray(String[] a) {
        int n = data.size();
        if (a.length < n) {
            a = new String[n];
        }
        
        for (int i = 0; i < n; i++) {
            a[i] = data.get(i).name;
        }
        
        for (int i = n; i < a.length; i++) {
            a[i] = null;
        }
        
        return a;
    }

    /**
     * Returns an array containing the timestamps of the elements in this
     * dataset in proper sequence (from first to last element). If the dataset
     * fits in the specified array, it is returned therein. Otherwise, a new
     * array is allocated with the size of this dataset.
     * <p>
     * If the dataset fits in the specified array with room to spare (i.e., the
     * array has more elements than the dataset), the element in the array
     * immediately following the end of the dataset is set to null.
     * 
     * @param a the array into which the timestamps of the elements in this
     * dataset are to be stored, if it is big enough; otherwise, a new array
     * is allocated for this purpose. 
     * @return an array containing the timestamps of the elements in this dataset.
     */
    public Timestamp[] toArray(Timestamp[] a) {
        int n = data.size();
        if (a.length < n) {
            a = new Timestamp[n];
        }
        
        for (int i = 0; i < n; i++) {
            a[i] = data.get(i).timestamp;
        }
        
        for (int i = n; i < a.length; i++) {
            a[i] = null;
        }
        
        return a;
    }
}
