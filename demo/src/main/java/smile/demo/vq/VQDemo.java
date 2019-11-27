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

package smile.demo.vq;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.io.Read;
import smile.plot.swing.ScatterPlot;

@SuppressWarnings("serial")
public abstract class VQDemo extends JPanel implements Runnable, ActionListener, AncestorListener {

    private static String[] datasetName = {
        "Gaussian/One", "Gaussian/Two", "Gaussian/Three",
        "Gaussian/Five", "Gaussian/Six",
        "NonConvex/Cross", "NonConvex/Pie",
        "Chameleon/t4.8k", "Chameleon/t5.8k",
        "Chameleon/t7.10k", "Chameleon/t8.8k",
        "Neural Gas/Discrete", "Neural Gas/Rectangle",
        "Neural Gas/Circle", "Neural Gas/Ring",
        "Neural Gas/Complex1", "Neural Gas/Complex2",
        "Neural Gas/Complex3", "Neural Gas/Complex4",
        "Neural Gas/HiLoDensity"
    };

    private static char[] delimiter = {
            ' ',  ' ',  ' ',
            '\t', ' ',
            '\t', '\t',
            ' ',  ' ',
            ' ',  ' ',
            '\t',  '\t',
            '\t',  '\t',
            '\t',  '\t',
            '\t',  '\t',
            '\t'
    };

    private static String[] datasource = {
        "clustering/gaussian/one.txt",
        "clustering/gaussian/two.txt",
        "clustering/gaussian/three.txt",
        "clustering/gaussian/five.txt",
        "clustering/gaussian/six.txt",
        "clustering/nonconvex/cross.txt",
        "clustering/nonconvex/pie.txt",
        "clustering/chameleon/t4.8k.txt",
        "clustering/chameleon/t5.8k.txt",
        "clustering/chameleon/t7.10k.txt",
        "clustering/chameleon/t8.8k.txt",
        "clustering/neuralgas/Discrete.txt",
        "clustering/neuralgas/Rectangle.txt",
        "clustering/neuralgas/Circle.txt",
        "clustering/neuralgas/Ring.txt",
        "clustering/neuralgas/Complex1.txt",
        "clustering/neuralgas/Complex2.txt",
        "clustering/neuralgas/Complex3.txt",
        "clustering/neuralgas/Complex4.txt",
        "clustering/neuralgas/HiLoDensity.txt"
    };

    static double[][][] dataset = new double[datasetName.length][][];
    static int datasetIndex = 0;
    static int clusterNumber = 2;
    static int epochs = 20;
    static double learningRate = 0.85;
    static double neighborhood = 5;

    JPanel optionPane;
    JComponent canvas;
    private JTextField clusterNumberField = new JTextField(Integer.toString(clusterNumber), 5);
    private JTextField epochField = new JTextField(Integer.toString(epochs), 5);
    private JTextField learningRateField = new JTextField(Double.toString(learningRate), 5);
    private JTextField neighborhoodField = new JTextField(Double.toString(neighborhood), 5);
    private JButton startButton;
    private JComboBox<String> datasetBox;
    char pointLegend = '.';

    /**
     * Constructor.
     */
    public VQDemo() {
        loadData(datasetIndex);
        addAncestorListener(this);

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
        optionPane.add(new JLabel("Epochs:"));
        optionPane.add(epochField);
        optionPane.add(new JLabel("Learning Rate:"));
        optionPane.add(learningRateField);
        optionPane.add(new JLabel("Neighborhood:"));
        optionPane.add(neighborhoodField);

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);

        canvas = ScatterPlot.plot(dataset[datasetIndex], '.');
        add(canvas, BorderLayout.CENTER);
    }

    /**
     * Execute the clustering algorithm and return a swing JComponent representing
     * the clusters.
     */
    public abstract JComponent learn();

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);

        try {
            JComponent plot = learn();
            if (plot != null) {
                remove(canvas);
                canvas = plot;
                add(canvas, BorderLayout.CENTER);
            }
            validate();
        } catch (Exception ex) {
            System.err.println(ex);
            ex.printStackTrace();
        }

        startButton.setEnabled(true);
        datasetBox.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            try {
                clusterNumber = Integer.parseInt(clusterNumberField.getText().trim());
                if (clusterNumber < 2) {
                    JOptionPane.showMessageDialog(this, "Invalid K: " + clusterNumber, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (clusterNumber > dataset[datasetIndex].length / 2) {
                    JOptionPane.showMessageDialog(this, "Too large K: " + clusterNumber, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                epochs = Integer.parseInt(epochField.getText().trim());
                if (epochs < 1) {
                    JOptionPane.showMessageDialog(this, "Invalid epochs: " + epochs, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                learningRate = Double.parseDouble(learningRateField.getText().trim());
                if (learningRate <= 0.0) {
                    JOptionPane.showMessageDialog(this, "Invalid learning rate: " + learningRate, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                neighborhood = Double.parseDouble(neighborhoodField.getText().trim());
                if (neighborhood < 1) {
                    JOptionPane.showMessageDialog(this, "Invalid neighborhood radius: " + neighborhood, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                return;
            }

            Thread thread = new Thread(this);
            thread.start();
        } else if ("datasetBox".equals(e.getActionCommand())) {
            datasetIndex = datasetBox.getSelectedIndex();
            loadData(datasetIndex);

            remove(canvas);
            if (dataset[datasetIndex].length < 500) {
                pointLegend = 'o';
            } else {
                pointLegend = '.';
            }
            canvas = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
            add(canvas, BorderLayout.CENTER);
            validate();
        }
    }

    @Override
    public void ancestorAdded(AncestorEvent event) {
        clusterNumberField.setText(Integer.toString(clusterNumber));

        if (datasetBox.getSelectedIndex() != datasetIndex) {
            datasetBox.setSelectedIndex(datasetIndex);
            remove(canvas);
            if (dataset[datasetIndex].length < 500) {
                pointLegend = 'o';
            } else {
                pointLegend = '.';
            }
            canvas = ScatterPlot.plot(dataset[datasetIndex], pointLegend);
            add(canvas, BorderLayout.CENTER);
            validate();
        }
    }

    @Override
    public void ancestorMoved(AncestorEvent event) {
    }

    @Override
    public void ancestorRemoved(AncestorEvent event) {
    }

    private void loadData(int datasetIndex) {
        if (dataset[datasetIndex] != null) return;

        CSVFormat format = CSVFormat.DEFAULT.withDelimiter(delimiter[datasetIndex]).withIgnoreSurroundingSpaces(true);
        try {
            DataFrame data = Read.csv(smile.util.Paths.getTestData(datasource[datasetIndex]), format);
            dataset[datasetIndex] = data.toArray();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, String.format("Failed to load dataset %s", datasetName[datasetIndex]), "ERROR", JOptionPane.ERROR_MESSAGE);
            System.err.println(e);
        }
    }
}
