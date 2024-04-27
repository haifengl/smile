/*
 * Copyright (c) 2010-2021 Haifeng Li. All rights reserved.
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

package smile.plot.swing;

import java.awt.Color;

/**
 * A dendrogram is a tree diagram frequently used to illustrate the arrangement
 * of the clusters produced by hierarchical clustering.
 *
 * @author Haifeng Li
 */
public class Dendrogram extends Plot {

    /**
     * The one end points of lines.
     */
    private final double[][] p1;
    /**
     * The other end points of lines.
     */
    private final double[][] p2;
    /**
     * The height of tree.
     */
    private final double height;

    /**
     * Constructor.
     * @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
     * step i of the clustering. If an element j in the row is less than n, then
     * observation j was merged at this stage. If {@code j >= n} then the merge
     * was with the cluster formed at the (earlier) stage j-n of the algorithm.
     * @param height a set of n-1 non-decreasing real values, which are the clustering height,
     * i.e., the value of the criterion associated with the clustering method
     * for the particular agglomeration.
     */
    public Dendrogram(int[][] merge, double[] height) {
        this(merge, height, Color.BLACK);
    }

    /**
     * Constructor.
     * @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
     * step i of the clustering. If an element j in the row is less than n, then
     * observation j was merged at this stage. If {@code j >= n} then the merge
     * was with the cluster formed at the (earlier) stage j-n of the algorithm.
     * @param height a set of n-1 non-decreasing real values, which are the clustering height,
     * i.e., the value of the criterion associated with the clustering method
     * for the particular agglomeration.
     * @param color the color for rendering the plot.
     */
    public Dendrogram(int[][] merge, double[] height, Color color) {
        super(color);
        int n = merge.length + 1;

        int[] order = new int[n];
        dfs(merge, n - 2, order, 0);

        double[][] pos = new double[2 * n - 1][2];
        for (int i = 0; i < n; i++) {
            pos[order[i]][0] = i;
            pos[order[i]][1] = 0;
        }

        for (int i = 0; i < merge.length; i++) {
            pos[n + i][0] = (pos[merge[i][0]][0] + pos[merge[i][1]][0]) / 2;
            pos[n + i][1] = Math.max(pos[merge[i][0]][1], pos[merge[i][1]][1]) + height[i];
        }

        p1 = new double[3 * n - 3][2];
        p2 = new double[3 * n - 3][2];

        for (int i = 0; i < merge.length; i++) {
            p1[3 * i][0] = pos[merge[i][0]][0];
            p1[3 * i][1] = pos[merge[i][0]][1];
            p2[3 * i][0] = pos[merge[i][0]][0];
            p2[3 * i][1] = pos[n + i][1];

            p1[3 * i + 1][0] = pos[merge[i][1]][0];
            p1[3 * i + 1][1] = pos[merge[i][1]][1];
            p2[3 * i + 1][0] = pos[merge[i][1]][0];
            p2[3 * i + 1][1] = pos[n + i][1];

            p1[3 * i + 2][0] = pos[merge[i][0]][0];
            p1[3 * i + 2][1] = pos[n + i][1];
            p2[3 * i + 2][0] = pos[merge[i][1]][0];
            p2[3 * i + 2][1] = pos[n + i][1];
        }

        this.height = pos[2 * n - 2][1];
    }

    /**
     * Returns the height of tree.
     */
    public double getHeight() {
        return height;
    }

    /**
     * DFS the tree to find the order of leafs to avoid the cross of lines in
     * the plot.
     */
    private int dfs(int[][] merge, int index, int[] order, int i) {
        int n = merge.length + 1;

        if (merge[index][0] > merge.length) {
            i = dfs(merge, merge[index][0] - n, order, i);
        } else {
            order[i++] = merge[index][0];
        }

        if (merge[index][1] > merge.length) {
            i = dfs(merge, merge[index][1] - n, order, i);
        } else {
            order[i++] = merge[index][1];
        }

        return i;
    }

    @Override
    public void paint(Graphics g) {
        Color c = g.getColor();
        g.setColor(color);

        for (int i = 0; i < p1.length; i++) {
            g.drawLine(p1[i], p2[i]);
        }

        g.setColor(c);
    }
    @Override
    public double[] getLowerBound() {
        int n = p1.length / 3 + 1;
        double[] bound = {-n / 100., 0};
        return bound;
    }

    @Override
    public double[] getUpperBound() {
        int n = p1.length / 3 + 1;
        double[] bound = {n + n / 100., 1.01 * height};
        return bound;
    }

    @Override
    public Canvas canvas() {
        Canvas canvas = new Canvas(getLowerBound(), getUpperBound(), false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(0).setTickVisible(false);

        canvas.add(this);
        return canvas;
    }
}
