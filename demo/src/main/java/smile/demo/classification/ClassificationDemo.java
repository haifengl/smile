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

package smile.demo.classification;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import smile.data.AttributeDataset;
import smile.data.NominalAttribute;
import smile.data.parser.DelimitedTextParser;
import smile.plot.Contour;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.ScatterPlot;

@SuppressWarnings("serial")
public abstract class ClassificationDemo extends JPanel implements Runnable, ActionListener, AncestorListener {

    private static String[] datasetName = {
        "Toy",
        "Big Toy",
    };

    private static String[] datasource = {
        "classification/toy/toy-train.txt",
        "classification/toy/toy-test.txt"
    };

    static AttributeDataset[] dataset = null;
    static int datasetIndex = 0;

    JPanel optionPane;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    char pointLegend = 'o';

    /**
     * Constructor.
     */
    public ClassificationDemo() {
        if (dataset == null) {
            dataset = new AttributeDataset[datasetName.length];
            DelimitedTextParser parser = new DelimitedTextParser();
            parser.setDelimiter("[\t ]+");
            parser.setResponseIndex(new NominalAttribute("class"), 0);
            try {
                dataset[datasetIndex] = parser.parse(datasetName[datasetIndex], smile.data.parser.IOUtils.getTestDataFile(datasource[datasetIndex]));
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                System.err.println(e);
            }
        }

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

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);

        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
        if (data.length < 500) {
            pointLegend = 'o';
        } else {
            pointLegend = '.';
        }
        PlotCanvas canvas = ScatterPlot.plot(data, pointLegend);
        for (int i = 0; i < data.length; i++) {
            canvas.point(pointLegend, Palette.COLORS[label[i]], data[i]);
        }
        add(canvas, BorderLayout.CENTER);
    }

    /**
     * Returns the error rate.
     */
    double error(int[] x, int[] y) {
        int e = 0;

        for (int i = 0; i < x.length; i++) {
            if (x[i] != y[i]) {
                e++;
            }
        }

        return (double) e / x.length;
    }

    /**
     * Execute the clustering algorithm and return a swing JComponent representing
     * the clusters.
     */
    public abstract double[][] learn(double[] x, double[] y);

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);

        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
        if (data.length < 500) {
            pointLegend = 'o';
        } else {
            pointLegend = '.';
        }
        PlotCanvas canvas = ScatterPlot.plot(data, pointLegend);
        for (int i = 0; i < data.length; i++) {
            canvas.point(pointLegend, Palette.COLORS[label[i]], data[i]);
        }

        double[] lower = canvas.getLowerBounds();
        double[] upper = canvas.getUpperBounds();

        double[] x = new double[50];
        double step = (upper[0] - lower[0]) / x.length;
        for (int i = 0; i < x.length; i++) {
            x[i] = lower[0] + step * (i+1);
        }

        double[] y = new double[50];
        step = (upper[1] - lower[1]) / y.length;
        for (int i = 0; i < y.length; i++) {
            y[i] = lower[1] + step * (i+1);
        }

        try {
        	double[][] f = learn(x, y);

        	if (f != null) {
        		for (int i = 0; i < y.length; i++) {
        			for (int j = 0; j < x.length; j++) {
        				double[] p = {x[j], y[i]};
        				canvas.point('.', Palette.COLORS[(int) f[i][j]], p);
        			}
        		}

        		double[] levels = {0.5};
        		Contour contour = new Contour(x, y, f, levels);
        		contour.showLevelValue(false);
        		canvas.add(contour);

        		BorderLayout layout = (BorderLayout) getLayout();
        		remove(layout.getLayoutComponent(BorderLayout.CENTER));
        		add(canvas, BorderLayout.CENTER);
        		validate();
        	}
        } catch (Exception ex) {
        	System.err.println(ex);
        }
        
        startButton.setEnabled(true);
        datasetBox.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            Thread thread = new Thread(this);
            thread.start();
        } else if ("datasetBox".equals(e.getActionCommand())) {
            datasetIndex = datasetBox.getSelectedIndex();

            if (dataset[datasetIndex] == null) {
                DelimitedTextParser parser = new DelimitedTextParser();
                parser.setDelimiter("[\t ]+");
                parser.setResponseIndex(new NominalAttribute("class"), 0);
                try {
                    dataset[datasetIndex] = parser.parse(datasetName[datasetIndex], smile.data.parser.IOUtils.getTestDataFile(datasource[datasetIndex]));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                    System.err.println(ex);
                }
            }

            double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
            int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
            if (data.length < 500) {
                pointLegend = 'o';
            } else {
                pointLegend = '.';
            }
            PlotCanvas canvas = ScatterPlot.plot(data, pointLegend);
            for (int i = 0; i < data.length; i++) {
                canvas.point(pointLegend, Palette.COLORS[label[i]], data[i]);
            }

            BorderLayout layout = (BorderLayout) getLayout();
            remove(layout.getLayoutComponent(BorderLayout.CENTER));
            add(canvas, BorderLayout.CENTER);
            validate();
        }
    }

    @Override
    public void ancestorAdded(AncestorEvent event) {
        if (datasetBox.getSelectedIndex() != datasetIndex) {
            datasetBox.setSelectedIndex(datasetIndex);

            double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
            int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
            PlotCanvas canvas = ScatterPlot.plot(data, 'o');
            for (int i = 0; i < data.length; i++) {
                canvas.point('o', Palette.COLORS[label[i]], data[i]);
            }

            BorderLayout layout = (BorderLayout) getLayout();
            remove(layout.getLayoutComponent(BorderLayout.CENTER));
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
}
