/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.clustering;

import java.io.Serial;
import java.io.Serializable;

/**
 * Clustering partitions. Partitioning methods classify the data points
 * into distinct non-overlapping groups.
 *
 * @author Haifeng Li
 */
public class Partitioning implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** The number of clusters. */
    int k;
    /** The cluster labels of data points. */
    int[] group;
    /** The number of data points in each cluster. */
    int[] size;

    /**
     * Constructor.
     * @param k the number of clusters.
     * @param group the cluster labels.
     */
    public Partitioning(int k, int[] group) {
        this.k = k;
        this.group = group;
        size = new int[k+1];
        for (int yi : group) {
            if (yi == Clustering.OUTLIER) {
                size[k]++;
            } else {
                size[yi]++;
            }
        }
    }

    /**
     * Returns the number of clusters.
     * @return the number of clusters.
     */
    public int k() {
        return k;
    }

    /**
     * Returns the cluster labels of data points.
     * @return the cluster labels of data points.
     */
    public int[] group() {
        return group;
    }

    /**
     * Returns the cluster label of i-th data point.
     * @param i the index of data point.
     * @return the cluster label.
     */
    public int group(int i) {
        return group[i];
    }

    /**
     * Returns the numbers of data points in each cluster.
     * @return the numbers of data points in each cluster.
     */
    public int[] size() {
        return size;
    }

    /**
     * Returns the number of data points of i-th cluster.
     * @param i the index of cluster.
     * @return the number of data points in i-th cluster.
     */
    public int size(int i) {
        return size[i];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < k; i++) {
            double r = 100.0 * size[i] / group.length;
            sb.append(String.format("Cluster %4d %6d (%4.1f%%)%n", i+1, size[i], r));
        }

        if (size[k] != 0) {
            double r = 100.0 * size[k] / group.length;
            sb.append(String.format("Outliers     %6d (%4.1f%%)%n", size[k], r));
        }

        sb.append(String.format("Total        %6d%n", group.length));
        return sb.toString();
    }
}
