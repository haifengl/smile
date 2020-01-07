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

import java.awt.*;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import smile.plot.swing.PlotCanvas;
import smile.plot.swing.ScatterPlot;
import smile.vq.BIRCH;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class BIRCHDemo extends VQDemo {
    private static final String ERROR = "Error";
    JTextField BNumberField;
    int B = 5;

    JTextField LNumberField;
    int L = 5;

    JTextField TNumberField;
    double T = 0.5;

    public BIRCHDemo() {
        BNumberField = new JTextField(Integer.toString(B), 5);
        LNumberField = new JTextField(Integer.toString(L), 5);
        TNumberField = new JTextField(Double.toString(T), 5);
        optionPane.add(new JLabel("B:"));
        optionPane.add(BNumberField);
        optionPane.add(new JLabel("L:"));
        optionPane.add(LNumberField);
        optionPane.add(new JLabel("T:"));
        optionPane.add(TNumberField);
    }

    @Override
    public JComponent learn() {
        try {
            B = Integer.parseInt(BNumberField.getText().trim());
            if (B < 2) {
                JOptionPane.showMessageDialog(this, "Invalid B: " + B, ERROR, JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Invalid B: " + BNumberField.getText(), ERROR, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            L = Integer.parseInt(LNumberField.getText().trim());
            if (L < 2) {
                JOptionPane.showMessageDialog(this, "Invalid L: " + L, ERROR, JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Invalid L: " + LNumberField.getText(), ERROR, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            T = Double.parseDouble(TNumberField.getText().trim());
            if (T <= 0) {
                JOptionPane.showMessageDialog(this, "Invalid T: " + T, ERROR, JOptionPane.ERROR_MESSAGE);
                return null;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid T: " + TNumberField.getText(), ERROR, JOptionPane.ERROR_MESSAGE);
            return null;
        }

        BIRCH birch = new BIRCH(2, B, L, T);

        PlotCanvas plot = ScatterPlot.plot(dataset[datasetIndex], pointLegend);

        int period = dataset[datasetIndex].length / 10;
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }


            for (int i = 0; i < dataset[datasetIndex].length; i++) {
                birch.update(dataset[datasetIndex][i]);

                if ((i + 1) % 100 == 0) {
                    plot.clear();
                    plot.points(dataset[datasetIndex], pointLegend);
                    double[][] neurons = birch.centroids();
                    plot.points(neurons, '@', Color.RED);
                    plot.repaint();

                    try {
                        Thread.sleep(100);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        thread.start();

        return plot;
    }

    @Override
    public String toString() {
        return "BIRCH";
    }

    public static void main(String argv[]) {
        BIRCHDemo demo = new BIRCHDemo();
        JFrame f = new JFrame("BIRCH");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
