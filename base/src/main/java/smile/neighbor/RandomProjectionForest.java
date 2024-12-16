/*
 * Copyright (c) 2010-2025 Haifeng Li. All rights reserved.
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
package smile.neighbor;

import java.util.ArrayList;
import java.util.List;

/**
 * A set of random projection trees.
 *
 * @author Karl Li
 */
public class RandomProjectionForest {
    /** Threshold for nearly zero comparison. */
    private static final double EPS = 1E-8;
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(RandomProjectionForest.class);
    private final List<FlatTree> trees;

    private RandomProjectionForest(List<FlatTree> trees) {
        this.trees = trees;
    }

    record FlatTree(double[][] hyperplanes, double[] offsets, int[][] children, int[][] indices) {
        public int[] search(double[] point) {
            int node = 0;
            while (children[node][0] > 0) {
                boolean rightSide = RandomProjectionTree.isRightSide(point, hyperplanes[node], offsets[node]);
                node = children[node][rightSide ? 1 : 0];
            }
            return indices[-children[node][0]];
        }
    }

    /**
     * Builds a random projection forest.
     * @param data the data set.
     * @param k the number of nearest neighbors
     * @param numTrees the number of trees
     * @param angular true for cosine metric, otherwise Euclidean
     * @return random projection forest
     */
    public static RandomProjectionForest of(double[][] data, int numTrees, int k, boolean angular) {
        ArrayList<FlatTree> trees = new ArrayList<>();
        int leafSize = Math.max(10, k);
        for (int i = 0; i < numTrees; i++) {
            RandomProjectionTree tree = RandomProjectionTree.of(data, leafSize, angular);
            trees.add(tree.flatten());
        }
        return new RandomProjectionForest(trees);
    }
}