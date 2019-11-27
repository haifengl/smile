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

package smile.demo.projection;

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

import org.apache.commons.csv.CSVFormat;
import smile.classification.FLD;
import smile.data.DataFrame;
import smile.data.formula.Formula;
import smile.io.Read;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.math.MathEx;

@SuppressWarnings("serial")
public class LDADemo extends JPanel implements Runnable, ActionListener {

    private static final String[] datasetName = {
        "IRIS", "Pen Digits"
    };

    private static final String[] datasource = {
        "classification/iris.txt",
        "classification/pendigits.txt"
    };

    protected static Formula[] formula = {
            Formula.lhs("Species"),
            Formula.lhs("V17"),
    };
    static DataFrame[] dataset = new DataFrame[datasetName.length];
    static int datasetIndex = 0;

    JPanel optionPane;
    JComponent canvas;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    char pointLegend = '.';

    /**
     * Constructor.
     */
    public LDADemo() {
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
     * Execute the projection algorithm and return a swing JComponent representing
     * the clusters.
     */
    public JComponent learn() {
        double[][] data = formula[datasetIndex].x(dataset[datasetIndex]).toArray();
        int[] labels = formula[datasetIndex].y(dataset[datasetIndex]).toIntArray();

        int min = MathEx.min(labels);
        for (int i = 0; i < labels.length; i++) {
            labels[i] -= min;
        }

        long clock = System.currentTimeMillis();
        FLD lda = FLD.fit(data, labels, MathEx.unique(labels).length > 3 ? 3 : 2, 1E-4);
        System.out.format("Learn LDA from %d samples in %dms\n", data.length, System.currentTimeMillis()-clock);

        double[][] y = lda.project(data);

        PlotCanvas plot = new PlotCanvas(MathEx.colMin(y), MathEx.colMax(y));
        if (labels != null) {
            for (int i = 0; i < y.length; i++) {
                plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
            }
        } else {
            plot.points(y, pointLegend);
        }

        plot.setTitle("Linear Discriminant Analysis");
        return plot;
    }

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);

        JComponent plot = learn();
        if (plot != null) {
            if (canvas != null)
                remove(canvas);
            canvas = plot;
            add(plot, BorderLayout.CENTER);
        }
        validate();

        startButton.setEnabled(true);
        datasetBox.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {
            datasetIndex = datasetBox.getSelectedIndex();

            if (dataset[datasetIndex] == null) {
                CSVFormat format = CSVFormat.DEFAULT.withDelimiter('\t');
                if (datasetIndex == 0) format = format.withFirstRecordAsHeader();

                try {
                    dataset[datasetIndex] = Read.csv(smile.util.Paths.getTestData(datasource[datasetIndex]), format);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null, String.format("Failed to load dataset %s", datasetName[datasetIndex]), "ERROR", JOptionPane.ERROR_MESSAGE);
                    System.out.println(ex);
                    ex.printStackTrace();
                }
            }

            if (dataset[datasetIndex].size() < 500) {
                pointLegend = 'o';
            } else {
                pointLegend = '.';
            }

            Thread thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public String toString() {
        return "Linear Discriminant Analysis";
    }
}
