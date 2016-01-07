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
package smile.demo.plot;

import java.awt.GridLayout;

import javax.swing.JFrame;
import javax.swing.JPanel;

import smile.data.parser.SparseMatrixParser;
import smile.math.matrix.SparseMatrix;
import smile.plot.Palette;
import smile.plot.PlotCanvas;
import smile.plot.SparseMatrixPlot;

/**
 *
 * @author Haifeng Li
 */
@SuppressWarnings("serial")
public class SparseMatrixPlotDemo extends JPanel {
    public SparseMatrixPlotDemo() {
        super(new GridLayout(1,2));

        SparseMatrixParser parser = new SparseMatrixParser();
        try {
            SparseMatrix m1 = parser.parse(smile.data.parser.IOUtils.getTestDataFile("matrix/08blocks.txt"));
            PlotCanvas canvas = SparseMatrixPlot.plot(m1);
            canvas.setTitle("08blocks");
            add(canvas);

            SparseMatrix m2 = parser.parse(smile.data.parser.IOUtils.getTestDataFile("matrix/mesh2em5.txt"));
            canvas = SparseMatrixPlot.plot(m2, Palette.jet(256));
            canvas.setTitle("mesh2em5");
            add(canvas);
        } catch (Exception ex) {
            System.err.println(ex);
        }
    }

    @Override
    public String toString() {
        return "Sparse Matrix Plot";
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Surface Plot");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().add(new SparseMatrixPlotDemo());
        frame.setVisible(true);
    }
}
