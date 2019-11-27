/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
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
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import smile.math.MathEx;
import smile.math.distance.EditDistance;
import smile.neighbor.BKTree;
import smile.neighbor.CoverTree;
import smile.neighbor.LinearSearch;
import smile.neighbor.Neighbor;
import smile.plot.swing.BarPlot;
import smile.plot.swing.PlotCanvas;

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
                smile.util.Paths.getTestDataLines("neighbor/index.noun")
                        .filter(line -> !line.startsWith(" "))
                        .forEach(line -> {
                            String[] w = line.split("\\s");
                            words.add(w[0].replace('_', ' '));
                        });
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

        int[] perm = MathEx.permutate(data.length);

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
