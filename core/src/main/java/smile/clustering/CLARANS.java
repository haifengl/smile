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
package smile.clustering;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import smile.math.Math;
import smile.math.distance.Distance;
import smile.util.MulticoreExecutor;

/**
 * Clustering Large Applications based upon RANdomized Search. CLARANS is an
 * efficient medoid-based clustering algorithm. The k-medoids algorithm is an
 * adaptation of the k-means algorithm. Rather than calculate the mean of the
 * items in each cluster, a representative item, or medoid, is chosen for each
 * cluster at each iteration. In CLARANS, the process of finding k medoids from
 * n objects is viewed abstractly as searching through a certain graph. In the
 * graph, a node is represented by a set of k objects as selected medoids. Two
 * nodes are neighbors if their sets differ by only one object. In each iteration,
 * CLARANS considers a set of randomly chosen neighbor nodes as candidate
 * of new medoids. We will move to the neighbor node if the neighbor
 * is a better choice for medoids. Otherwise, a local optima is discovered. The
 * entire process is repeated multiple time to find better.
 * <p>
 * CLARANS has two parameters: the maximum number of neighbors examined
 * (maxNeighbor) and the number of local minima obtained (numLocal). The
 * higher the value of maxNeighbor, the closer is CLARANS to PAM, and the
 * longer is each search of a local minima. But the quality of such a local
 * minima is higher and fewer local minima needs to be obtained.
 *
 * <h2>References</h2>
 * <ol>
 * <li>R. Ng and J. Han. CLARANS: A Method for Clustering Objects for Spatial Data Mining. IEEE TRANS. KNOWLEDGE AND DATA ENGINEERING, 2002.</li>
 * </ol>
 * 
 * @param <T> the type of input object.
 * 
 * @author Haifeng Li
 */
public class CLARANS <T> extends PartitionClustering<T> implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(CLARANS.class);

    /**
     * The total distortion.
     */
    double distortion;
    /**
     * The distance measure for calculation of distortion.
     */
    private Distance<T> distance;
    /**
     * The number of local minima to search for.
     */
    private int numLocal;
    /**
     * The maximum number of neighbors examined during a search of local minima.
     */
    private int maxNeighbor;
    /**
     * The medoids of each cluster.
     */
    T[] medoids;

    /**
     * Constructor. Clustering data into k clusters. The maximum number of
     * random search is set to 0.02 * k * (n - k), where n is the number of
     * data and k is the number clusters. The number of local searches is 
     * max(8, numProcessors).
     * 
     * @param data the dataset for clustering.
     * @param distance the distance/dissimilarity measure.
     * @param k the number of clusters.
     */
    public CLARANS(T[] data, Distance<T> distance, int k) {
        this(data, distance, k, (int) Math.round(0.0125 * k * (data.length - k)));
    }
    
    /**
     * Constructor. Clustering data into k clusters.
     * @param data the dataset for clustering.
     * @param distance the distance/dissimilarity measure.
     * @param k the number of clusters.
     * @param maxNeighbor the maximum number of neighbors examined during a random search of local minima.
     */
    public CLARANS(T[] data, Distance<T> distance, int k, int maxNeighbor) {
        this(data, distance, k, maxNeighbor, Math.max(2, MulticoreExecutor.getThreadPoolSize()));        
    }
    
    /**
     * Constructor. Clustering data into k clusters.
     * @param data the dataset for clustering.
     * @param distance the distance/dissimilarity measure.
     * @param k the number of clusters.
     * @param maxNeighbor the maximum number of neighbors examined during a random search of local minima.
     * @param numLocal the number of local minima to search for.
     */
    public CLARANS(T[] data, Distance<T> distance, int k, int maxNeighbor, int numLocal) {
        if (maxNeighbor <= 0) {
            throw new IllegalArgumentException("Invalid maxNeighbor: " + maxNeighbor);
        }
        
        if (numLocal <= 0) {
            throw new IllegalArgumentException("Invalid numLocal: " + numLocal);            
        }
        
        int n = data.length;

        if (k >= n) {
            throw new IllegalArgumentException("Too large k: " + k);
        }

        if (maxNeighbor > n) {
            throw new IllegalArgumentException("Too large maxNeighbor: " + maxNeighbor);
        }

        int minmax = 100;
        if (k * (n - k) < minmax) {
            minmax = k * (n - k);
        }
        
        if (maxNeighbor < minmax) {
            maxNeighbor = minmax;
        }
        
        this.k = k;
        this.distance = distance;
        this.numLocal = numLocal;
        this.maxNeighbor = maxNeighbor;
        
        List<CLARANSTask> tasks = new ArrayList<>();
        for (int i = 0; i < numLocal; i++) {
            tasks.add(new CLARANSTask(data));
        }

        try {
            MulticoreExecutor.run(tasks);
        } catch (Exception e) {
            logger.error("Failed to run CLARANS on multi-core", e);

            for (CLARANSTask task : tasks) {
                task.call();
            }
        }
        
        distortion = Double.POSITIVE_INFINITY;
        for (CLARANSTask task : tasks) {
            if (task.distortion < distortion) {
                distortion = task.distortion;
                medoids = task.medoids;
                y = task.y;
            }
        }
        
        size = new int[k];
        for (int i = 0; i < n; i++) {
            size[y[i]]++;
        }
    }

    /**
     * Adapter for running one local of CLARANS in thread pool.
     */
    class CLARANSTask implements Callable<CLARANSTask> {
        final T[] data;
        
        double distortion;
        T[] medoids;
        int[] y;

        CLARANSTask(T[] data) {
            this.data = data;
        }

        @Override
        @SuppressWarnings("unchecked")
        public CLARANSTask call() {
            int n = data.length;
            medoids = (T[]) java.lang.reflect.Array.newInstance(data.getClass().getComponentType(), k);
            T[] newMedoids = medoids.clone();
            y = new int[n];
            int[] newY = new int[n];
            double[] d = new double[n];
            double[] newD = new double[n];
            
            distortion = seed(distance, data, medoids, y, d);

            System.arraycopy(medoids, 0, newMedoids, 0, k);
            System.arraycopy(y, 0, newY, 0, n);
            System.arraycopy(d, 0, newD, 0, n);
            
            for (int neighborCount = 1; neighborCount <= maxNeighbor; neighborCount++) {
                double randomNeighborDistortion = getRandomNeighbor(data, newMedoids, newY, newD);
                if (randomNeighborDistortion < distortion) {
                    neighborCount = 0;
                    distortion = randomNeighborDistortion;
                    System.arraycopy(newMedoids, 0, medoids, 0, k);
                    System.arraycopy(newY, 0, y, 0, n);
                    System.arraycopy(newD, 0, d, 0, n);
                } else {
                    System.arraycopy(medoids, 0, newMedoids, 0, k);
                    System.arraycopy(y, 0, newY, 0, n);
                    System.arraycopy(d, 0, newD, 0, n);
                }
            }

            return this;
        }
    }
    
    /**
     * Generate a random neighbor which differs in only one medoid with current clusters.
     */
    private double getRandomNeighbor(T[] data, T[] medoids, int[] y, double[] d) {
        int n = data.length;

        int index = Math.randomInt(k);
        T medoid = null;
        boolean dup;
        do {
            dup = false;
            medoid = data[Math.randomInt(n)];
            for (int i = 0; i < k; i++) {
                if (medoid == medoids[i]) {
                    dup = true;
                    break;
                }
            }
        } while (dup);

        medoids[index] = medoid;

        for (int i = 0; i < n; i++) {
            double dist = distance.d(data[i], medoid);
            if (d[i] > dist) {
                y[i] = index;
                d[i] = dist;
            } else if (y[i] == index) {
                d[i] = dist;
                y[i] = index;
                for (int j = 0; j < k; j++) {
                    if (j != index) {
                        dist = distance.d(data[i], medoids[j]);
                        if (d[i] > dist) {
                            y[i] = j;
                            d[i] = dist;
                        }
                    }
                }
            }
        }

        return Math.sum(d);
    }
    
    /**
     * Returns the number of local minima to search for.
     */
    public int getNumLocalMinima() {
        return numLocal;
    }

    /**
     * Returns the maximum number of neighbors examined during a search of local minima.
     */
    public int getMaxNeighbor() {
        return maxNeighbor;
    }

    /**
     * Returns the distortion.
     */
    public double distortion() {
        return distortion;
    }

    /**
     * Returns the medoids.
     */
    public T[] medoids() {
        return medoids;
    }

    /**
     * Cluster a new instance.
     * @param x a new instance.
     * @return the cluster label, which is the index of nearest medoid.
     */
    @Override
    public int predict(T x) {
        double minDist = Double.MAX_VALUE;
        int bestCluster = 0;

        for (int i = 0; i < k; i++) {
            double dist = distance.d(x, medoids[i]);
            if (dist < minDist) {
                minDist = dist;
                bestCluster = i;
            }
        }

        return bestCluster;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append(String.format("CLARANS distortion: %.5f%n", distortion));
        sb.append(String.format("Clusters of %d data points:%n", y.length));
        for (int i = 0; i < k; i++) {
            int r = (int) Math.round(1000.0 * size[i] / y.length);
            sb.append(String.format("%3d\t%5d (%2d.%1d%%)%n", i, size[i], r / 10, r % 10));
        }
        
        return sb.toString();
    }
}
