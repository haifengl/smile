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
package smile.plot;

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
    private double[][] p1;
    /**
     * The other end points of lines.
     */
    private double[][] p2;
    /**
     * The height of tree.
     */
    private double height;

    /**
     * Constructor.
     * @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
     * step i of the clustering. If an element j in the row is less than n, then
     * observation j was merged at this stage. If j &ge; n then the merge
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
     * observation j was merged at this stage. If j &ge; n then the merge
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
        g.setColor(getColor());

        for (int i = 0; i < p1.length; i++) {
            g.drawLine(p1[i], p2[i]);
        }

        g.setColor(c);
    }

    /**
     * Create a dendrogram plot.
     * @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
     * step i of the clustering. If an element j in the row is less than n, then
     * observation j was merged at this stage. If j &ge; n then the merge
     * was with the cluster formed at the (earlier) stage j-n of the algorithm.
     * @param height a set of n-1 non-decreasing real values, which are the clustering height,
     * i.e., the value of the criterion associated with the clustering method
     * for the particular agglomeration.
     */
    public static PlotCanvas plot(int[][] merge, double[] height) {
        int n = merge.length + 1;
        Dendrogram dendrogram = new Dendrogram(merge, height);

        double[] lowerBound = {-n / 100, 0};
        double[] upperBound = {n + n / 100, 1.01 * dendrogram.getHeight()};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(0).setLabelVisible(false);

        canvas.add(dendrogram);

        return canvas;
    }

    /**
     * Create a dendrogram plot.
     * @param id the id of the plot.
     * @param merge an n-1 by 2 matrix of which row i describes the merging of clusters at
     * step i of the clustering. If an element j in the row is less than n, then
     * observation j was merged at this stage. If j &ge; n then the merge
     * was with the cluster formed at the (earlier) stage j-n of the algorithm.
     * @param height a set of n-1 non-decreasing real values, which are the clustering height,
     * i.e., the value of the criterion associated with the clustering method
     * for the particular agglomeration.
     */
    public static PlotCanvas plot(String id, int[][] merge, double[] height) {
        int n = merge.length + 1;
        Dendrogram dendrogram = new Dendrogram(merge, height);

        double[] lowerBound = {-n / 100, 0};
        double[] upperBound = {n + n / 100, 1.01 * dendrogram.getHeight()};

        PlotCanvas canvas = new PlotCanvas(lowerBound, upperBound, false);
        canvas.getAxis(0).setGridVisible(false);
        canvas.getAxis(0).setLabelVisible(false);

        dendrogram.setID(id);
        canvas.add(dendrogram);

        return canvas;
    }
}
