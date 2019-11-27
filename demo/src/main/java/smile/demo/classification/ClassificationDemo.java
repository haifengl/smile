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
import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.data.type.DataTypes;
import smile.data.type.StructType;
import smile.data.type.StructField;
import smile.io.Read;
import smile.plot.swing.Contour;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

@SuppressWarnings("serial")
public abstract class ClassificationDemo extends JPanel implements Runnable, ActionListener, AncestorListener {

    protected static String[] datasetName = {
        "Toy",
        "Big Toy",
    };

    protected static String[] datasource = {
        "classification/toy/toy-train.txt",
        "classification/toy/toy-test.txt"
    };

    static CSVFormat format = CSVFormat.DEFAULT.withDelimiter('\t');
    static StructType schema = DataTypes.struct(
            new StructField("class", DataTypes.IntegerType),
            new StructField("V1", DataTypes.DoubleType),
            new StructField("V2", DataTypes.DoubleType)
    );
    static Formula formula = Formula.lhs("class");
    static DataFrame[] dataset = new DataFrame[datasource.length];
    static int datasetIndex = 0;

    JPanel optionPane;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    char pointLegend = 'o';

    /**
     * Constructor.
     */
    public ClassificationDemo() {
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

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);

        double[][] data = formula.x(dataset[datasetIndex]).toArray();
        int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        
        if (data.length < 500) {
            pointLegend = 'o';
        } else {
            pointLegend = '.';
        }
        
        PlotCanvas canvas = paintOnCanvas(data, label);
        add(canvas, BorderLayout.CENTER);
    }
    
    /**
     * paint given data with label on canvas
     * @param data the data point(s) to paint, only support 2D or 3D features
     * @param label the data label for classification
     */
    protected PlotCanvas paintOnCanvas(double[][] data, int[] label) {
        PlotCanvas canvas = ScatterPlot.plot(data, pointLegend);
        for (int i = 0; i < data.length; i++) {
            canvas.point(pointLegend, Palette.COLORS[label[i]], data[i]);
        }
        return canvas;
    }
    
    /**
     * Get the class number dependent contour levels, typically we would have k - 1 contour levels for k class
     */
    protected double[] getContourLevels() {
        return new double[]{0.5};
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

        double[][] data = formula.x(dataset[datasetIndex]).toArray();
        int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        
        if (data.length < 500) {
            pointLegend = 'o';
        } else {
            pointLegend = '.';
        }
        
        PlotCanvas canvas = paintOnCanvas(data, label);

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

                double[] levels = getContourLevels();
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
            loadData(datasetIndex);

            double[][] data = formula.x(dataset[datasetIndex]).toArray();
            int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        
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

            double[][] data = formula.x(dataset[datasetIndex]).toArray();
            int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        
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

    private void loadData(int datasetIndex) {
        if (dataset[datasetIndex] != null) return;

        CSVFormat format = CSVFormat.DEFAULT.withDelimiter('\t').withIgnoreSurroundingSpaces(true);
        try {
            dataset[datasetIndex] = Read.csv(smile.util.Paths.getTestData(datasource[datasetIndex]), format, schema);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, String.format("Failed to load dataset %s", datasetName[datasetIndex]), "ERROR", JOptionPane.ERROR_MESSAGE);
            System.err.println(e);
        }
    }
}
