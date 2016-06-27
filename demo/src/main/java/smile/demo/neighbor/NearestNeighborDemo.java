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
package smile.demo.neighbor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;

import smile.plot.PlotCanvas;
import smile.math.Math;
import smile.math.distance.EuclideanDistance;
import smile.neighbor.CoverTree;
import smile.neighbor.KDTree;
import smile.neighbor.LSH;
import smile.neighbor.LinearSearch;
import smile.neighbor.MPLSH;
import smile.neighbor.Neighbor;
import smile.plot.BarPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class NearestNeighborDemo extends JPanel implements Runnable, ActionListener {

    private String[] label = {"Naive", "KD-Tree", "Cover Tree", "LSH", "MPLSH"};
    private JPanel optionPane;
    private JPanel canvas;
    private JButton startButton;
    private JSlider logNSlider;
    private JSlider dimensionSlider;
    private int logN = 4;
    private int dimension = 10;

    public NearestNeighborDemo() {
        super(new BorderLayout());

        startButton = new JButton("Start");
        startButton.setActionCommand("startButton");
        startButton.addActionListener(this);

        Hashtable<Integer, JLabel> logNLabelTable = new Hashtable<>();
        for (int i = 3; i <= 7; i++) {
            logNLabelTable.put(new Integer(i), new JLabel(String.valueOf(i)));
        }

        logNSlider = new JSlider(3, 7, logN);
        logNSlider.setLabelTable(logNLabelTable);
        logNSlider.setMajorTickSpacing(1);
        logNSlider.setPaintTicks(true);
        logNSlider.setPaintLabels(true);

        Hashtable<Integer, JLabel> dimensionLabelTable = new Hashtable<>();
        dimensionLabelTable.put(new Integer(2), new JLabel(String.valueOf(2)));
        for (int i = 20; i <= 120; i += 20) {
            dimensionLabelTable.put(new Integer(i), new JLabel(String.valueOf(i)));
        }

        dimensionSlider = new JSlider(2, 128, dimension);
        dimensionSlider.setLabelTable(dimensionLabelTable);
        dimensionSlider.setMajorTickSpacing(20);
        dimensionSlider.setMinorTickSpacing(5);
        dimensionSlider.setPaintTicks(true);
        dimensionSlider.setPaintLabels(true);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(startButton);
        optionPane.add(new JLabel("log N:"));
        optionPane.add(logNSlider);
        optionPane.add(new JLabel("Dimension:"));
        optionPane.add(dimensionSlider);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(1, 2));
        canvas.setBackground(Color.WHITE);
        add(canvas, BorderLayout.CENTER);
    }

    @Override
    public void run() {
        startButton.setEnabled(false);
        logNSlider.setEnabled(false);
        dimensionSlider.setEnabled(false);

        logN = logNSlider.getValue();
        dimension = dimensionSlider.getValue();

        System.out.println("Generating dataset...");
        int n = (int) Math.pow(10, logN);
        double[][] data = new double[n][];
        for (int i = 0; i < n; i++) {
            data[i] = new double[dimension];
            for (int j = 0; j < dimension; j++) {
                data[i][j] = Math.random();
            }
        }

        int[] perm = Math.permutate(n);

        System.out.println("Building searching data structure...");
        long time = System.currentTimeMillis();
        LinearSearch<double[]> naive = new LinearSearch<>(data, new EuclideanDistance());
        int naiveBuild = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        KDTree<double[]> kdtree = new KDTree<>(data, data);
        int kdtreeBuild = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        CoverTree<double[]> cover = new CoverTree<>(data, new EuclideanDistance());
        int coverBuild = (int) (System.currentTimeMillis() - time);

        System.out.println("Perform 100 searches...");
        int[] answer = new int[100];
        double radius = 0.0;
        time = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            Neighbor<double[], double[]> neighbor = naive.nearest(data[perm[i]]);
            answer[i] = neighbor.index;
            radius += neighbor.distance;
        }
        int naiveSearch = (int) (System.currentTimeMillis() - time);
        radius /= 100;

        time = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            kdtree.nearest(data[perm[i]]);
        }
        int kdtreeSearch = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            cover.nearest(data[perm[i]]);
        }
        int coverSearch = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        LSH<double[]> lsh = new LSH<>(dimension, 5, (int) Math.ceil(Math.log2(dimension)), 4 * radius, 1017881);
        for (int i = 0; i < n; i++) {
            lsh.put(data[i], data[i]);
        }
        int lshBuild = (int) (System.currentTimeMillis() - time);

        double lshRecall = 0.0;
        time = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            if (lsh.nearest(data[perm[i]]).index == answer[i]) {
                lshRecall++;
            }
        }
        int lshSearch = (int) (System.currentTimeMillis() - time);
        lshRecall /= 100;
        System.out.format("The recall of LSH is %.1f%%\n", lshRecall * 100);

        time = System.currentTimeMillis();
        MPLSH<double[]> mplsh = new MPLSH<>(dimension, 5, (int) Math.ceil(Math.log2(n)), 4 * radius, 1017881);
        for (int i = 0; i < n; i++) {
            mplsh.put(data[i], data[i]);
        }
        double[][] train = new double[1000][];
        for (int i = 0; i < train.length; i++) {
            train[i] = data[perm[i]];
        }
        mplsh.learn(kdtree, train, 1.5 * radius);
        int mplshBuild = (int) (System.currentTimeMillis() - time);

        double mplshRecall = 0.0;
        time = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            if (mplsh.nearest(data[perm[i]], 0.95, 10).index == answer[i]) {
                mplshRecall++;
            }
        }
        int mplshSearch = (int) (System.currentTimeMillis() - time);
        mplshRecall /= 100;
        System.out.format("The recall of MPLSH is %.1f%%\n", mplshRecall * 100);

        canvas.removeAll();
        double[] buildTime = {naiveBuild, kdtreeBuild, coverBuild, lshBuild, mplshBuild};
        PlotCanvas build = BarPlot.plot(buildTime, label);
        build.setTitle("Build Time");
        canvas.add(build);

        double[] searchTime = {naiveSearch, kdtreeSearch, coverSearch, lshSearch, mplshSearch};
        PlotCanvas search = BarPlot.plot(searchTime, label);
        search.setTitle("Search Time");
        canvas.add(search);
        validate();

        startButton.setEnabled(true);
        logNSlider.setEnabled(true);
        dimensionSlider.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            Thread thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public String toString() {
        return "Nearest Neighbor";
    }

    public static void main(String argv[]) {
        NearestNeighborDemo demo = new NearestNeighborDemo();
        JFrame f = new JFrame("Nearest Neighbor");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
