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

import java.awt.Dimension;

import javax.swing.JFrame;

import smile.classification.LDA;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class LDADemo extends ClassificationDemo {

    /**
     * Constructor.
     */
    public LDADemo() {
    }

    @Override
    public double[][] learn(double[] x, double[] y) {
        double[][] data = dataset[datasetIndex].toArray(new double[dataset[datasetIndex].size()][]);
        int[] label = dataset[datasetIndex].toArray(new int[dataset[datasetIndex].size()]);
        
        LDA lda = new LDA(data, label);
        for (int i = 0; i < label.length; i++) {
            label[i] = lda.predict(data[i]);
        }
        double trainError = error(label, label);

        System.out.format("training error = %.2f%%\n", 100*trainError);

        double[][] z = new double[y.length][x.length];
        for (int i = 0; i < y.length; i++) {
            for (int j = 0; j < x.length; j++) {
                double[] p = {x[j], y[i]};
                z[i][j] = lda.predict(p);
            }
        }

        return z;
    }

    @Override
    public String toString() {
        return "Linear Disiminant Analysis";
    }

    public static void main(String argv[]) {
        ClassificationDemo demo = new LDADemo();
        JFrame f = new JFrame("Linear Disiminant Analysis");
        f.setSize(new Dimension(1000, 1000));
        f.setLocationRelativeTo(null);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(demo);
        f.setVisible(true);
    }
}
