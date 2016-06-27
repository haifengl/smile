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
package smile.demo.mds;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import smile.data.Attribute;
import smile.data.AttributeDataset;
import smile.data.parser.DelimitedTextParser;
import smile.mds.IsotonicMDS;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

@SuppressWarnings("serial")
public class IsotonicMDSDemo extends JPanel implements Runnable, ActionListener {

    private static final String[] datasetName = {
        "British Towns", "Euro Cities", "Morse Code", "Color Stimuli", "Lloyds Bank Employees 1905-1909", "Lloyds Bank Employees 1925-1929"
    };

    private static final String[] datasource = {
        "projection/BritishTowns.txt",
        "projection/eurodist.txt",
        "projection/morsecode.txt",
        "projection/colorstimuli.txt",
        "projection/bank05d.txt",
        "projection/bank25d.txt"
    };

    static AttributeDataset[] dataset = new AttributeDataset[datasetName.length];
    static int datasetIndex = 0;

    JPanel optionPane;
    JComponent canvas;
    private JButton startButton;
    private JComboBox<String> datasetBox;

    /**
     * Constructor.
     */
    public IsotonicMDSDemo() {
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

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);
    }

    /**
     * Execute the MDS algorithm and return a swing JComponent representing
     * the clusters.
     */
    public JComponent learn() {
        JPanel pane = new JPanel(new GridLayout(1, 2));
        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        String[] labels = dataset[datasetIndex].toArray(new String[dataset[datasetIndex].size()]);
        if (labels[0] == null) {
            Attribute[] attr = dataset[datasetIndex].attributes();
            labels = new String[attr.length];
            for (int i = 0; i < labels.length; i++) {
                labels[i] = attr[i].getName();
            }
        }

        long clock = System.currentTimeMillis();
        IsotonicMDS isomds = new IsotonicMDS(data, 2);
        System.out.format("Learn Kruskal's Nonmetric MDS (k=2) from %d samples in %dms\n", data.length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(isomds.getCoordinates(), labels);
        plot.setTitle("Kruskal's Nonmetric MDS (k = 2)");
        pane.add(plot);

        clock = System.currentTimeMillis();
        isomds = new IsotonicMDS(data, 3);
        System.out.format("Learn Kruskal's Nonmetric MDS (k=3) from %d samples in %dms\n", data.length, System.currentTimeMillis()-clock);

        plot = ScatterPlot.plot(isomds.getCoordinates(), labels);
        plot.setTitle("Kruskal's Nonmetric MDS (k = 3)");
        pane.add(plot);

        return pane;
    }

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);

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
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            datasetIndex = datasetBox.getSelectedIndex();

            if (dataset[datasetIndex] == null) {
                DelimitedTextParser parser = new DelimitedTextParser();
                parser.setDelimiter("[\t]+");
                parser.setRowNames(true);
                parser.setColumnNames(true);
                if (datasetIndex == 2 || datasetIndex == 3) {
                    parser.setRowNames(false);
                }

                try {
                    dataset[datasetIndex] = parser.parse(datasetName[datasetIndex], smile.data.parser.IOUtils.getTestDataFile(datasource[datasetIndex]));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                    System.err.println(ex);
                }
            }

            Thread thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public String toString() {
        return "Kruskal's Nonmetric MDS";
    }

    public static void main(String argv[]) {
        IsotonicMDSDemo demo = new IsotonicMDSDemo();
        JFrame f = new JFrame("Kruskal's Nonmetric MDS");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
