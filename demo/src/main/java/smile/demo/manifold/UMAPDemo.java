/*
 * Copyright (c) 2010-2020 Haifeng Li. All rights reserved.
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
 */

package smile.demo.manifold;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

import org.apache.commons.csv.CSVFormat;
import smile.data.CategoricalEncoder;
import smile.data.DataFrame;
import smile.io.Read;
import smile.manifold.UMAP;
import smile.plot.swing.Canvas;
import smile.plot.swing.ScatterPlot;

/**
 * Visualization Demo for {@link UMAP} algorithm
 * 
 * @author rayeaster
 */
@SuppressWarnings("serial")
public class UMAPDemo extends JPanel implements Runnable, ActionListener {

    private static final int DEFAULT_NEIGHBORS = 15;
    int neighbors = DEFAULT_NEIGHBORS;
    JTextField neighborsField;

    private static String[] datasetName = {"MNIST"};

    double[][] data;
    int[] labels;

    JPanel optionPane;
    JComponent canvas;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    char pointLegend = '@';

    public UMAPDemo() {
        startButton = new JButton("Start");
        startButton.setActionCommand("startButton");
        startButton.addActionListener(this);

        datasetBox = new JComboBox<>();
        for (int i = 0; i < datasetName.length; i++) {
            datasetBox.addItem(datasetName[i]);
        }
        datasetBox.setSelectedIndex(0);
        datasetBox.setActionCommand("datasetBox");
        datasetBox.addActionListener(this);

        optionPane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        optionPane.setBorder(BorderFactory.createRaisedBevelBorder());
        optionPane.add(startButton);
        optionPane.add(new JLabel("Dataset:"));
        optionPane.add(datasetBox);

        neighborsField = new JTextField(Integer.toString(neighbors), 5);
        optionPane.add(new JLabel("Neighbors:"));
        optionPane.add(neighborsField);

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);
    }

    public JComponent learn() {
        JPanel pane = new JPanel(new GridLayout(1, 2));
        
        long clock = System.currentTimeMillis();
        UMAP umap = UMAP.of(data, neighbors);
        System.out.format("Learn UMAP from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        double[][] x = umap.coordinates;
        int[] y = new int[umap.index.length];
        for (int i = 0; i < y.length; i++) {
            y[i] = labels[umap.index[i]];
        }

        Canvas plot = ScatterPlot.of(x, y, '@').canvas();

        plot.setTitle("UMAP");
        pane.add(plot.panel());

        return pane;
    }

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);
        neighborsField.setEnabled(false);

        try {
            JComponent plot = learn();
            if (plot != null) {
                if (canvas != null)
                    remove(canvas);
                canvas = plot;
                add(plot, BorderLayout.CENTER);
            }
            validate();
        } catch (Exception ex) {
            System.err.println(ex);
        }

        startButton.setEnabled(true);
        datasetBox.setEnabled(true);
        neighborsField.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {

            try {
                CSVFormat format = CSVFormat.DEFAULT.withDelimiter(' ').withIgnoreSurroundingSpaces(true);
                DataFrame dataset = Read.csv(smile.util.Paths.getTestData("mnist/mnist2500_X.txt"), format);
                data = dataset.toArray(false, CategoricalEncoder.ONE_HOT);

                dataset = Read.csv(smile.util.Paths.getTestData("mnist/mnist2500_labels.txt"));
                labels = dataset.column(0).toIntArray();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                System.err.println(ex);
            }

            try {
                neighbors = Integer.parseInt(neighborsField.getText().trim());
                if (neighbors < 2 || neighbors > 100) {
                    JOptionPane.showMessageDialog(this, "Invalid Neighbors: " + neighbors, "Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid Neighbors: " + neighborsField.getText(), "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            Thread thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public String toString() {
        return "UMAP";
    }

    public static void main(String[] args) {
        UMAPDemo demo = new UMAPDemo();
        JFrame f = new JFrame("UMAP");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
