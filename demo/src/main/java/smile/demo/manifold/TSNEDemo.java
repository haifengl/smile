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

package smile.demo.manifold;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.*;

import org.apache.commons.csv.CSVFormat;
import smile.data.DataFrame;
import smile.io.Read;
import smile.plot.swing.Palette;
import smile.plot.swing.PlotCanvas;
import smile.manifold.TSNE;
import smile.math.MathEx;
import smile.projection.PCA;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class TSNEDemo extends JPanel implements Runnable, ActionListener {

    int perplexity = 20;
    JTextField perplexityField;

    private static String[] datasetName = {
            "MNIST"
    };

    double[][] data;
    int[] labels;

    JPanel optionPane;
    JComponent canvas;
    private JButton startButton;
    private JComboBox<String> datasetBox;
    char pointLegend = '@';

    public TSNEDemo() {
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

        perplexityField = new JTextField(Integer.toString(perplexity), 5);
        optionPane.add(new JLabel("Perplexity:"));
        optionPane.add(perplexityField);

        setLayout(new BorderLayout());
        add(optionPane, BorderLayout.NORTH);
    }

    public JComponent learn() {
        JPanel pane = new JPanel(new GridLayout(1, 2));

        PCA pca = PCA.fit(data);
        pca.setProjection(50);
        double[][] X = pca.project(data);
        long clock = System.currentTimeMillis();
        TSNE tsne = new TSNE(X, 2, perplexity, 200, 1000);
        System.out.format("Learn t-SNE from %d samples in %dms\n", data.length, System.currentTimeMillis() - clock);

        double[][] y = tsne.coordinates;

        PlotCanvas plot = new PlotCanvas(MathEx.colMin(y), MathEx.colMax(y));

        for (int i = 0; i < y.length; i++) {
            plot.point(pointLegend, Palette.COLORS[labels[i]], y[i]);
        }

        plot.setTitle("t-SNE");
        pane.add(plot);

        return pane;
    }

    @Override
    public void run() {
        startButton.setEnabled(false);
        datasetBox.setEnabled(false);
        perplexityField.setEnabled(false);

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
        perplexityField.setEnabled(true);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if ("startButton".equals(e.getActionCommand())) {

            try {
                CSVFormat format = CSVFormat.DEFAULT.withDelimiter(' ').withIgnoreSurroundingSpaces(true);
                DataFrame dataset = Read.csv(smile.util.Paths.getTestData("mnist/mnist2500_X.txt"), format);
                data = dataset.toArray();

                dataset = Read.csv(smile.util.Paths.getTestData("mnist/mnist2500_labels.txt"));
                labels = dataset.column(0).toIntArray();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(null, "Failed to load dataset.", "ERROR", JOptionPane.ERROR_MESSAGE);
                System.err.println(ex);
            }

            try {
                perplexity = Integer.parseInt(perplexityField.getText().trim());
                if (perplexity < 10 || perplexity > 300) {
                    JOptionPane.showMessageDialog(this, "Invalid Perplexity: " + perplexity, "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Invalid K: " + perplexityField.getText(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Thread thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    public String toString() {
        return "t-SNE";
    }

    public static void main(String argv[]) {
        TSNEDemo demo = new TSNEDemo();
        JFrame f = new JFrame("t-SNE");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
