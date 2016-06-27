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
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import smile.plot.PlotCanvas;
import smile.math.Math;
import smile.math.distance.EditDistance;
import smile.neighbor.BKTree;
import smile.neighbor.CoverTree;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;
import smile.plot.BarPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class ApproximateStringSearchDemo extends JPanel implements Runnable, ActionListener  {

    private String[] label = {"Naive", "BK-Tree", "Cover Tree"};
    private JPanel optionPane;
    private JPanel canvas;
    private JButton startButton;
    private JTextField knnField;
    private int knn = 1;
    private String[] data;
    private BKTree<String> bktree;
    private CoverTree<String> cover;
    private LinearSearch<String> naive;

    public ApproximateStringSearchDemo() {
        super(new BorderLayout());

        startButton = new JButton("Start");
        startButton.setActionCommand("startButton");
        startButton.addActionListener(this);

        knnField = new JTextField(Integer.toString(knn), 5);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(startButton);
        optionPane.add(new JLabel("K:"));
        optionPane.add(knnField);

        add(optionPane, BorderLayout.NORTH);

        canvas = new JPanel(new GridLayout(1, 2));
        canvas.setBackground(Color.WHITE);
        add(canvas, BorderLayout.CENTER);
    }

    @Override
    public void run() {
        startButton.setEnabled(false);
        knnField.setEnabled(false);

        if (data == null) {
            System.out.print("Loading dataset...");
            List<String> words = new ArrayList<>();

            try {
                FileInputStream stream = new FileInputStream(smile.data.parser.IOUtils.getTestDataFile("index.noun"));
                BufferedReader input = new BufferedReader(new InputStreamReader(stream));
                String line = input.readLine();
                while (line != null) {
                    if (!line.startsWith(" ")) {
                        String[] w = line.split("\\s");
                        words.add(w[0].replace('_', ' '));
                    }
                    line = input.readLine();
                }
            } catch (Exception e) {
                System.err.println(e);
            }

            data = words.toArray(new String[1]);
            System.out.println(words.size() + " words");
            
            System.out.println("Building searching data structure...");
            long time = System.currentTimeMillis();
            naive = new LinearSearch<>(data, new EditDistance(50, true));
            int naiveBuild = (int) (System.currentTimeMillis() - time) / 1000;

            time = System.currentTimeMillis();
            bktree = new BKTree<>(new EditDistance(50, true));
            bktree.add(data);
            int bktreeBuild = (int) (System.currentTimeMillis() - time) / 1000;

            time = System.currentTimeMillis();
            cover = new CoverTree<>(data, new EditDistance(50, true));
            int coverBuild = (int) (System.currentTimeMillis() - time) / 1000;

            double[] buildTime = {naiveBuild, bktreeBuild, coverBuild};
            PlotCanvas build = BarPlot.plot(buildTime, label);
            build.setTitle("Build Time");
            canvas.add(build);
            validate();
        }

        int[] perm = Math.permutate(data.length);

        System.out.println("Perform 1000 searches...");
        long time = System.currentTimeMillis();
        List<Neighbor<String, String>> neighbors = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            naive.range(data[perm[i]], knn, neighbors);
            neighbors.clear();
        }
        int naiveSearch = (int) (System.currentTimeMillis() - time) / 1000;

        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            bktree.range(data[perm[i]], knn, neighbors);
            neighbors.clear();
        }
        int kdtreeSearch = (int) (System.currentTimeMillis() - time) / 1000;

        time = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            cover.range(data[perm[i]], knn, neighbors);
            neighbors.clear();
        }
        int coverSearch = (int) (System.currentTimeMillis() - time) / 1000;

        double[] searchTime = {naiveSearch, kdtreeSearch, coverSearch};
        PlotCanvas search = BarPlot.plot(searchTime, label);
        search.setTitle("Search Time of k = " + knn);
        canvas.add(search);
        if (canvas.getComponentCount() > 3)
            canvas.setLayout(new GridLayout(2,2));
        validate();

        startButton.setEnabled(true);
        knnField.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            try {
                knn = Integer.parseInt(knnField.getText().trim());
                if (knn < 1) {
                    JOptionPane.showMessageDialog(this, "Invalid K: " + knn, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid K: " + knnField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Thread thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public String toString() {
        return "Approximate String Search";
    }

    public static void main(String argv[]) {
        ApproximateStringSearchDemo demo = new ApproximateStringSearchDemo();
        JFrame f = new JFrame("Approximate String Search");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo( null );
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
