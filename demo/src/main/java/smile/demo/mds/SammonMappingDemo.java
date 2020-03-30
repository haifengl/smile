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

package smile.demo.mds;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.io.Read;
import smile.mds.SammonMapping;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;

@SuppressWarnings("serial")
public class SammonMappingDemo extends JPanel implements Runnable, ActionListener {

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

    static DataFrame[] dataset = new DataFrame[datasetName.length];
    static int datasetIndex = 0;

    JPanel optionPane;
    JComponent canvas;
    private JButton startButton;
    private JComboBox<String> datasetBox;

    /**
     * Constructor.
     */
    public SammonMappingDemo() {
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
        double[][] data = datasetIndex == 2 || datasetIndex == 3 ? dataset[datasetIndex].toArray() : dataset[datasetIndex].drop(0).toArray();
        String[] labels = datasetIndex == 0 || datasetIndex == 1 ? dataset[datasetIndex].stringVector(0).toArray() : dataset[datasetIndex].names();

        long clock = System.currentTimeMillis();
        SammonMapping sammon = SammonMapping.of(data, 2);
        System.out.format("Learn Sammon's Mapping (k=2) from %d samples in %dms\n", data.length, System.currentTimeMillis()-clock);

        PlotCanvas plot = ScatterPlot.plot(sammon.coordinates, labels);
        plot.setTitle("Sammon's Mapping (k = 2)");
        pane.add(plot);

        clock = System.currentTimeMillis();
        sammon = SammonMapping.of(data, 3);
        System.out.format("Learn Sammon's Mapping (k=3) from %d samples in %dms\n", data.length, System.currentTimeMillis()-clock);

        plot = ScatterPlot.plot(sammon.coordinates, labels);
        plot.setTitle("Sammon's Mapping (k = 3)");
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
                if (canvas != null) remove(canvas);
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
                CSVFormat format = CSVFormat.DEFAULT.withDelimiter('\t');
                if (datasetIndex > 1) {
                    format = format.withFirstRecordAsHeader();
                }

                try {
                    dataset[datasetIndex] = Read.csv(smile.util.Paths.getTestData(datasource[datasetIndex]), format);
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
        return "Sammon's Mapping";
    }

    public static void main(String argv[]) {
        SammonMappingDemo demo = new SammonMappingDemo();
        JFrame f = new JFrame("Sammon's Mapping");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
