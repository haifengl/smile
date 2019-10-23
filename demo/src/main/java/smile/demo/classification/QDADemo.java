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

import java.awt.Dimension;

import javax.swing.JFrame;

import smile.classification.QDA;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class QDADemo extends ClassificationDemo {

    /**
     * Constructor.
     */
    public QDADemo() {
    }

    @Override
    public double[][] learn(double[] x, double[] y) {
        double[][] data = formula.x(dataset[datasetIndex]).toArray();
        int[] label = formula.y(dataset[datasetIndex]).toIntArray();
        
        QDA qda = QDA.fit(data, label);
        int[] pred = new int[label.length];
        for (int i = 0; i < label.length; i++) {
            pred[i] = qda.predict(data[i]);
        }
        double trainError = error(label, pred);

        System.out.format("training error = %.2f%%\n", 100*trainError);

        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = qda.predict(p);
            }
        }

        return z;
    }

    @Override
    public String toString() {
        return "Quadratic Disiminant Analysis";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new QDADemo();
        JFrame f = new JFrame("Quadratic Disiminant Analysis");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
