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
import java.util.ArrayList;
import java.util.Hashtable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;

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
public class RNNSearchDemo extends JPanel implements Runnable, ActionListener {

    private String[] label = {"Naive", "KD-Tree", "Cover Tree", "LSH", "MPLSH"};
    private JPanel optionPane;
    private JPanel canvas;
    private JButton startButton;
    private JSlider logNSlider;
    private JSlider dimensionSlider;
    private JTextField radiusField;
    private int logN = 4;
    private int dimension = 10;
    private double radius = 0.5;

    public RNNSearchDemo() {
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

        radiusField = new JTextField(Double.toString(radius), 5);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(startButton);
        optionPane.add(new JLabel("log N:"));
        optionPane.add(logNSlider);
        optionPane.add(new JLabel("Dimension:"));
        optionPane.add(dimensionSlider);
        optionPane.add(new JLabel("Radius:"));
        optionPane.add(radiusField);

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
        radiusField.setEnabled(false);

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

        time = System.currentTimeMillis();
        LSH<double[]> lsh = new LSH<>(dimension, 5, (int) Math.log2(dimension), 4 * radius, 1017881);
        for (int i = 0; i < n; i++) {
            lsh.put(data[i], data[i]);
        }
        int lshBuild = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        MPLSH<double[]> mplsh = new MPLSH<>(dimension, 3, (int) Math.log2(n), 4 * radius, 1017881);
        for (int i = 0; i < n; i++) {
            mplsh.put(data[i], data[i]);
        }
        double[][] train = new double[1000][];
        for (int i = 0; i < train.length; i++) {
            train[i] = data[perm[i]];
        }
        mplsh.learn(kdtree, train, radius);
        int mplshBuild = (int) (System.currentTimeMillis() - time);

        System.out.println("Perform 1000 searches...");
        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            ArrayList<Neighbor<double[], double[]>> neighbors = new ArrayList<>();
            naive.range(data[perm[i]], radius, neighbors);
        }
        int naiveSearch = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            ArrayList<Neighbor<double[], double[]>> neighbors = new ArrayList<>();
            kdtree.range(data[perm[i]], radius, neighbors);
        }
        int kdtreeSearch = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            ArrayList<Neighbor<double[], double[]>> neighbors = new ArrayList<>();
            cover.range(data[perm[i]], radius, neighbors);
        }
        int coverSearch = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            ArrayList<Neighbor<double[], double[]>> neighbors = new ArrayList<>();
            lsh.range(data[perm[i]], radius, neighbors);
        }
        int lshSearch = (int) (System.currentTimeMillis() - time);

        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            ArrayList<Neighbor<double[], double[]>> neighbors = new ArrayList<>();
            mplsh.range(data[perm[i]], radius, neighbors, 0.95, 10);
        }
        int mplshSearch = (int) (System.currentTimeMillis() - time);

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
        radiusField.setEnabled(false);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            try {
                radius = Double.parseDouble(radiusField.getText().trim());
                if (radius <= 0.0) {
                    JOptionPane.showMessageDialog(this, "Invalid Radius: " + radius, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid Radius: " + radiusField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Thread thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public String toString() {
        return "Range Search";
    }

    public static void main(String argv[]) {
        RNNSearchDemo demo = new RNNSearchDemo();
        JFrame f = new JFrame("Range Search");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
